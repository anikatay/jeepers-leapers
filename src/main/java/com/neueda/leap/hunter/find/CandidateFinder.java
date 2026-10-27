package com.neueda.leap.hunter.find;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.neueda.leap.ai.ModelCallException;
import com.neueda.leap.ai.OpenAiJsonClient;
import com.neueda.leap.hunter.CheckVerdict;
import com.neueda.leap.hunter.HunterLimits;
import com.neueda.leap.hunter.HunterModelSettings;
import com.neueda.leap.hunter.catalog.GicsSector;
import com.neueda.leap.hunter.spec.HunterSpec;
import com.neueda.leap.hunter.spec.HunterSpec.SectorOp;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Asks the model, in one call, for S&P 500 companies that match a spec (section 5 of the spec). */
@Component
public class CandidateFinder {

    /** Part of each run's spec hash; change it whenever the prompt or schema changes. */
    public static final String PROMPT_VERSION = "finder-1";
    static final int MAX_ASKED = 20;

    private static final ObjectMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public record Reply(List<Candidate> candidates) {
        public Reply {
            candidates = candidates == null ? List.of() : candidates;
        }
    }

    public record Candidate(String ticker, List<Estimate> estimates, List<Judgement> criteria,
            List<String> whyItMatched) {
        public Candidate {
            estimates = estimates == null ? List.of() : estimates;
            criteria = criteria == null ? List.of() : criteria;
            whyItMatched = whyItMatched == null ? List.of() : whyItMatched;
        }
    }

    public record Estimate(String ruleId, BigDecimal value, String period) {}

    public record Judgement(String criterionId, CheckVerdict verdict, String reason) {}

    public record Result(List<Candidate> candidates, OpenAiJsonClient.Completion completion) {}

    private final OpenAiJsonClient client;
    private final HunterModelSettings settings;

    public CandidateFinder(OpenAiJsonClient client, HunterModelSettings settings) {
        this.client = client;
        this.settings = settings;
    }

    public Result find(HunterSpec spec) {
        OpenAiJsonClient.Completion completion = client.complete(new OpenAiJsonClient.Request(
                settings.model(), systemPrompt(), userContent(spec), "hunter_candidates", SCHEMA,
                settings.maxOutputTokens(), settings.reasoningEffort()));
        try {
            return new Result(JSON.readValue(completion.content(), Reply.class).candidates(), completion);
        } catch (IOException e) {
            throw new ModelCallException("The model's candidates were not valid JSON", e);
        }
    }

    static int candidatesToAskFor(HunterSpec spec) {
        return Math.min(spec.candidateLimit() + HunterLimits.EXTRA_CANDIDATES, MAX_ASKED);
    }

    /** Only enabled rules and checks are sent; nothing about the user is. */
    String userContent(HunterSpec spec) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("knowledgeCutoff", settings.knowledgeCutoff().toString());
        content.put("universe", "S&P 500");
        content.put("maxCandidates", candidatesToAskFor(spec));
        content.put("numericRules", spec.enabledQuantRules()
                .map(rule -> orderedMap(
                        "ruleId", rule.id(),
                        "metric", rule.metric().id(),
                        "meaning", rule.metric().label() + ": " + rule.metric().definition(),
                        "unit", rule.metric().unit(),
                        "condition", rule.op().describe(rule.value(), rule.value2())))
                .toList());
        content.put("sectors", spec.sectorRuleEnabled()
                ? orderedMap(
                        "rule", spec.sectorRule().op() == SectorOp.IN ? "in one of these" : "in none of these",
                        "values", spec.sectorRule().values().stream().map(GicsSector::label).toList())
                : null);
        content.put("checks", spec.enabledCriteria()
                .map(criterion -> orderedMap("criterionId", criterion.id(), "statement", criterion.statement()))
                .toList());
        try {
            return JSON.writeValueAsString(content);
        } catch (IOException e) {
            throw new ModelCallException("Could not serialize the hunter", e);
        }
    }

    String systemPrompt() {
        String cutoff = settings.cutoffDate();
        return """
                You find S&P 500 companies that match an investor's rules, using only your general knowledge, \
                which ends on %s. You have no live data, filings or news.

                You receive one JSON object:
                - numericRules: conditions on figures such as gross margin. For each company you return, \
                estimate every figure for the latest reported fiscal period you know and name that period, for \
                example "FY2025". Use null when you don't know. Percentages are in percent units: 50 means 50%%. \
                Market cap is in billions of US dollars.
                - sectors: when present, the company's GICS sector must be in one of these, or in none of \
                these, as the rule says.
                - checks: statements to judge for each company you return. Give MET, PARTIAL, NOT_MET or \
                UNKNOWN, and one short sentence saying why. Use UNKNOWN when you are not sure.
                - maxCandidates: the most companies to return.

                Rules:
                - Return only companies in the S&P 500 that you know well, as tickers in Yahoo Finance form \
                (BRK-B, not BRK.B). Skip any company you are unsure about.
                - Never return a company that fails a numeric rule by your own estimate.
                - Return the closest matches first. Return fewer companies, or none, rather than weak matches.
                - Give an estimate for every numeric rule and a verdict for every check, using the given \
                ruleId and criterionId.
                - whyItMatched: up to 3 short plain-English sentences on why the company fits the rules.
                - Never quote anything, and never name a document, filing, report, earnings call, article, \
                press release or date as a source. Describe what is generally known.
                - Never mention events after %s.
                - Describe the company, never whether its stock is a good investment. Never use words such as \
                buy, sell, hold, undervalued, overvalued, upside, price target, bargain or recommend.
                """.formatted(cutoff, cutoff);
    }

    private static Map<String, Object> object(Map<String, Object> properties) {
        return Map.of("type", "object", "additionalProperties", false,
                "required", List.copyOf(properties.keySet()), "properties", properties);
    }

    private static Map<String, Object> orderedMap(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static final Map<String, Object> SCHEMA = object(orderedMap(
            "candidates", Map.of("type", "array", "items", object(orderedMap(
                    "ticker", Map.of("type", "string"),
                    "estimates", Map.of("type", "array", "items", object(orderedMap(
                            "ruleId", Map.of("type", "string"),
                            "value", Map.of("type", List.of("number", "null")),
                            "period", Map.of("type", List.of("string", "null"))))),
                    "criteria", Map.of("type", "array", "items", object(orderedMap(
                            "criterionId", Map.of("type", "string"),
                            "verdict", Map.of("type", "string",
                                    "enum", Arrays.stream(CheckVerdict.values()).map(Enum::name).toList()),
                            "reason", Map.of("type", List.of("string", "null"))))),
                    "whyItMatched", Map.of("type", "array", "items", Map.of("type", "string")))))));
}
