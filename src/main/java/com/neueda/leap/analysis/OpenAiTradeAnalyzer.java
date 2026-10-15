package com.neueda.leap.analysis;

import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Sends one trade and its context to OpenAI and returns a structured verdict. */
public class OpenAiTradeAnalyzer {

    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
    // Reasoning models spend part of this budget on hidden reasoning tokens.
    private static final int MAX_OUTPUT_TOKENS = 2000;
    private static final int MAX_ERROR_BODY_CHARS = 500;

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();

    private static final String SYSTEM_PROMPT = """
            You review one stock trade made by a retail investor and judge whether it was a good decision.

            You receive a single JSON object:
            - trade: the trade being reviewed.
            - outcome: its profit and loss so far, or null if that has not been calculated yet.
              realizedPnl applies to sells; unrealizedPnl applies to buys, on the quantity still held.
              returnPct is the gain or loss per share: exit against entry for a sell, the current
              price against entry for a buy that is still held; null when it cannot be worked out.
              pnlStatus is MATCHED, PARTIAL or UNMATCHED. For a buy, UNMATCHED means none of it has
              been sold, and MATCHED or PARTIAL means all or part of it has since been sold: the
              profit on the sold shares is recorded against those sells and is not shown here, so
              realizedPnl of 0 on a buy does not mean it broke even. For a sell, UNMATCHED means
              there is no recorded purchase, so the cost basis and profit are unknown.
            - priceNow: the current price and how far it has moved since the trade.
            - portfolio: cash and holdings as they stand now, after the trade. weightPct is each
              holding's share of totalValue, and tradeValuePctOfTotal is the size of this trade
              against totalValue.
            - trackRecord: the investor's overall results, or null if there is no history.
            - recentTrades: their latest trades, including this one.
            Percentages are in percent units (12.5 means 12.5%). Money is in portfolio.currency.

            Rules:
            - Use only the figures in the JSON. Do not use anything you know about the company, its
              price history, news or the wider market.
            - The data contains no price history, no market benchmark, no fees or taxes and no
              sector information, so you cannot judge market timing. Do not guess at any of these.
            - Every figure is already calculated. Quote figures as given; do not recalculate them.
            - Judge the decision as well as the result: position size against the portfolio,
              concentration in one holding, cash left afterwards and the pattern in recent trades
              count as much as profit or loss.

            Reply with:
            - verdict: GOOD, MIXED, POOR or TOO_EARLY. Use TOO_EARLY when there is no result to judge
              yet and the sizing and concentration give no clear reason to call the decision good or
              poor.
            - summary: two or three plain sentences addressed to the investor as "you".
            - strengths, concerns, suggestions: up to three short items each; use an empty list when
              nothing applies. Suggestions are about how to trade in future, never a recommendation
              to buy or sell a particular stock.
            """;

    private static final Map<String, Object> STRING_LIST =
            Map.of("type", "array", "items", Map.of("type", "string"));

    private static final Map<String, Object> RESULT_SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("verdict", "summary", "strengths", "concerns", "suggestions"),
            "properties", Map.of(
                    "verdict", Map.of(
                            "type", "string",
                            "enum", Arrays.stream(TradeAnalysisResult.Verdict.values()).map(Enum::name).toList()),
                    "summary", Map.of("type", "string"),
                    "strengths", STRING_LIST,
                    "concerns", STRING_LIST,
                    "suggestions", STRING_LIST));

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public OpenAiTradeAnalyzer(String apiKey, String model, String baseUrl) {
        this(RestClient.builder().requestFactory(timeoutFactory()), apiKey, model, baseUrl);
    }

    OpenAiTradeAnalyzer(RestClient.Builder builder, String apiKey, String model, String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    public TradeAnalysisResult analyze(TradeAnalysisContext context) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new TradeAnalysisException("OpenAI API key is not configured");
        }
        if (model == null || model.isBlank()) {
            throw new TradeAnalysisException("OpenAI model is not configured");
        }

        byte[] responseBody;
        try {
            responseBody = restClient.post()
                    .uri("/chat/completions")
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(context))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new TradeAnalysisException(
                                "OpenAI returned HTTP " + response.getStatusCode().value() + ": " + errorBody(response));
                    })
                    .body(byte[].class);
        } catch (RestClientException e) {
            throw new TradeAnalysisException("Could not reach OpenAI: " + e.getMessage(), e);
        }
        return parse(responseBody);
    }

    private byte[] requestBody(TradeAnalysisContext context) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_completion_tokens", MAX_OUTPUT_TOKENS,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", JSON.writeValueAsString(context))),
                    "response_format", Map.of(
                            "type", "json_schema",
                            "json_schema", Map.of(
                                    "name", "trade_analysis",
                                    "strict", true,
                                    "schema", RESULT_SCHEMA)));
            return JSON.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new TradeAnalysisException("Could not serialize the trade context", e);
        }
    }

    private TradeAnalysisResult parse(byte[] responseBody) {
        if (responseBody == null || responseBody.length == 0) {
            throw new TradeAnalysisException("OpenAI returned an empty response");
        }
        try {
            JsonNode choice = JSON.readTree(responseBody).path("choices").path(0);
            JsonNode message = choice.path("message");

            if (message.hasNonNull("refusal")) {
                throw new TradeAnalysisException(
                        "OpenAI declined to analyse the trade: " + message.get("refusal").asText());
            }
            if ("length".equals(choice.path("finish_reason").asText())) {
                throw new TradeAnalysisException("OpenAI's reply was cut off at the output token limit");
            }

            String content = message.path("content").asText("");
            if (content.isBlank()) {
                throw new TradeAnalysisException("OpenAI returned no analysis");
            }

            TradeAnalysisResult result = JSON.readValue(content, TradeAnalysisResult.class);
            if (result.verdict() == null || result.summary() == null || result.summary().isBlank()) {
                throw new TradeAnalysisException("OpenAI's analysis is missing a verdict or summary");
            }
            return result;
        } catch (IOException e) {
            throw new TradeAnalysisException("OpenAI's reply was not valid analysis JSON", e);
        }
    }

    private static String errorBody(ClientHttpResponse response) throws IOException {
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8).strip();
        return body.length() <= MAX_ERROR_BODY_CHARS ? body : body.substring(0, MAX_ERROR_BODY_CHARS) + "...";
    }

    private static ClientHttpRequestFactory timeoutFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}
