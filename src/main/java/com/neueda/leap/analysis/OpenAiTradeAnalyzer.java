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
              A null field inside outcome means it does not apply to this trade or is unknown; never
              read a null as zero.
              realizedPnl is the profit on a sell. It is null for buys, and null for a sell with no
              recorded purchase (pnlStatus UNMATCHED), whose profit is unknown.
              unrealizedPnl is the paper gain or loss on the shares of a buy that are still held. It is
              null for sells, and null for a buy that has been sold in full (pnlStatus MATCHED): the
              profit on sold shares is recorded against the sells, not shown here.
              returnPct is the gain or loss per share: exit against entry for a sell, the current
              price against entry for a buy that is still held.
              pnlStatus is MATCHED, PARTIAL or UNMATCHED: for a buy, how much of it has since been sold
              (all, some, none); for a sell, whether a recorded purchase covers it (fully, partly, not at all).
            - priceNow: the current price and how far it has moved since the trade.
            - portfolio: cash and holdings as they stand today, which may be long after the trade.
              cashPct and each holding's weightPct are shares of totalValue. tradeValuePctOfTotal
              compares this trade with today's portfolio, not the portfolio at the time of the trade.
            - trackRecord: the investor's overall results, or null if there is no history.
            - recentTrades: the account's 10 most recent trades, newest first. This trade is among
              them only if it is one of the latest 10; an older trade is not listed there.
            Percentages are in percent units (12.5 means 12.5%). Money is in portfolio.currency.

            Rules:
            - Use only the figures in the JSON. When you cite a number, use one that appears in the JSON,
              exactly as given; do not calculate new ones.
            - The only prices you know are the ones in the JSON. Never compare with any other price,
              such as the price shortly before or after the trade.
            - The data has no price history, benchmark, news, fees, taxes, sectors or volatility, so do
              not mention or advise on market conditions, market trends, momentum, news, volatility,
              sectors or industries, the economy, timing the market, or order types such as stop-loss
              orders. For example, never write "monitor market conditions", "positive momentum",
              "a volatile stock", "a single sector" or "set a stop-loss". Concentration always means
              concentration in one stock.
            - Describe cash by its cashPct: above 40% is a large cash buffer, not a small one.
            - Judge the decision as well as the result: position size against the portfolio,
              concentration in one holding, cash left afterwards and the pattern in recent trades
              count as much as profit or loss.

            Reply with:
            - verdict: GOOD, MIXED, POOR or TOO_EARLY. Use TOO_EARLY when there is no result to judge
              yet and the sizing and concentration give no clear reason to call the decision good or
              poor. When the profit is unknown, the verdict cannot be GOOD or POOR on profit alone.
            - summary: two or three plain sentences addressed to the investor as "you".
            - strengths, concerns, suggestions: at most three short items each; use an empty list when
              nothing applies. Suggestions are about the investor's own process: position size,
              concentration, cash buffer, and reviewing their results. Never recommend buying or
              selling a particular stock.
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

    /** One analysis and what it cost; model is the version OpenAI reports having used. */
    public record Analysis(
            TradeAnalysisResult result,
            String model,
            int promptTokens,
            int completionTokens,
            long latencyMs) {}

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
        return analyzeWithUsage(context).result();
    }

    public Analysis analyzeWithUsage(TradeAnalysisContext context) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new TradeAnalysisException("OpenAI API key is not configured");
        }
        if (model == null || model.isBlank()) {
            throw new TradeAnalysisException("OpenAI model is not configured");
        }

        long startedAt = System.nanoTime();
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
        return parse(responseBody, (System.nanoTime() - startedAt) / 1_000_000);
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

    private Analysis parse(byte[] responseBody, long latencyMs) {
        if (responseBody == null || responseBody.length == 0) {
            throw new TradeAnalysisException("OpenAI returned an empty response");
        }
        try {
            JsonNode root = JSON.readTree(responseBody);
            JsonNode choice = root.path("choices").path(0);
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
            JsonNode usage = root.path("usage");
            return new Analysis(result, root.path("model").asText(model),
                    usage.path("prompt_tokens").asInt(0), usage.path("completion_tokens").asInt(0), latencyMs);
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
