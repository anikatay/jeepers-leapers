package com.neueda.leap.analysis;

import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neueda.leap.ai.ModelCallException;
import com.neueda.leap.ai.OpenAiJsonClient;

import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Sends one trade and its context to OpenAI and returns a structured verdict. */
public class OpenAiTradeAnalyzer {

    public static final String DEFAULT_BASE_URL = OpenAiJsonClient.DEFAULT_BASE_URL;

    // Reasoning models spend part of this budget on hidden reasoning tokens.
    private static final int MAX_OUTPUT_TOKENS = 2000;

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

    private final OpenAiJsonClient client;
    private final String model;

    public OpenAiTradeAnalyzer(String apiKey, String model, String baseUrl) {
        this.client = new OpenAiJsonClient(apiKey, baseUrl, OpenAiJsonClient.DEFAULT_READ_TIMEOUT);
        this.model = model;
    }

    OpenAiTradeAnalyzer(RestClient.Builder builder, String apiKey, String model, String baseUrl) {
        this.client = new OpenAiJsonClient(builder, apiKey, baseUrl);
        this.model = model;
    }

    public TradeAnalysisResult analyze(TradeAnalysisContext context) {
        return analyzeWithUsage(context).result();
    }

    public Analysis analyzeWithUsage(TradeAnalysisContext context) {
        OpenAiJsonClient.Completion completion;
        try {
            completion = client.complete(new OpenAiJsonClient.Request(model, SYSTEM_PROMPT,
                    JSON.writeValueAsString(context), "trade_analysis", RESULT_SCHEMA, MAX_OUTPUT_TOKENS, null));
        } catch (IOException e) {
            throw new TradeAnalysisException("Could not serialize the trade context", e);
        } catch (ModelCallException e) {
            throw new TradeAnalysisException(e.getMessage(), e);
        }

        TradeAnalysisResult result;
        try {
            result = JSON.readValue(completion.content(), TradeAnalysisResult.class);
        } catch (IOException e) {
            throw new TradeAnalysisException("OpenAI's reply was not valid analysis JSON", e);
        }
        if (result.verdict() == null || result.summary() == null || result.summary().isBlank()) {
            throw new TradeAnalysisException("OpenAI's analysis is missing a verdict or summary");
        }
        return new Analysis(result, completion.model(), completion.promptTokens(), completion.completionTokens(),
                completion.latencyMs());
    }
}
