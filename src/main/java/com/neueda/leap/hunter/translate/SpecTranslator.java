package com.neueda.leap.hunter.translate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.neueda.leap.ai.ModelCallException;
import com.neueda.leap.ai.OpenAiJsonClient;
import com.neueda.leap.hunter.HunterLimits;
import com.neueda.leap.hunter.HunterModelSettings;
import com.neueda.leap.hunter.catalog.GicsSector;
import com.neueda.leap.hunter.catalog.Metric;
import com.neueda.leap.hunter.check.AdviceGuard;
import com.neueda.leap.hunter.spec.HunterSpec;
import com.neueda.leap.hunter.spec.HunterSpec.Criterion;
import com.neueda.leap.hunter.spec.HunterSpec.NumericOp;
import com.neueda.leap.hunter.spec.HunterSpec.QuantRule;
import com.neueda.leap.hunter.spec.HunterSpec.SectorOp;
import com.neueda.leap.hunter.spec.HunterSpec.SectorRule;
import com.neueda.leap.hunter.spec.HunterSpec.UnsupportedClause;
import com.neueda.leap.hunter.spec.HunterSpec.UnsupportedReason;
import com.neueda.leap.hunter.spec.HunterSpecValidator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Turns a plain-English idea into an unsaved Hunter Spec (section 4.3 of the spec). */
@Component
public class SpecTranslator {

    /** Part of the draft cache key; change it whenever the prompt or schema changes. */
    static final String PROMPT_VERSION = "translator-2";
    static final String DEFAULT_NAME = "My hunter";

    private static final ObjectMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /** A draft and what it cost; the completion is null when the draft came from the cache. */
    public record Translation(HunterSpec spec, OpenAiJsonClient.Completion completion) {}

    record Reply(String name, List<ReplyQuantRule> quantRules, ReplySectorRule sectorRule,
            List<ReplyCriterion> criteria, List<UnsupportedClause> unsupported) {}

    record ReplyQuantRule(Metric metric, NumericOp op, BigDecimal value, BigDecimal value2, String sourceText) {}

    record ReplySectorRule(SectorOp op, List<GicsSector> values, String sourceText) {}

    record ReplyCriterion(String statement, String sourceText) {}

    private final OpenAiJsonClient client;
    private final HunterModelSettings settings;
    private final Map<String, HunterSpec> cache;

    public SpecTranslator(OpenAiJsonClient client, HunterModelSettings settings,
            @Value("${hunter.draft-cache.max-entries}") int cacheMaxEntries) {
        this.client = client;
        this.settings = settings;
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, HunterSpec> eldest) {
                return size() > cacheMaxEntries;
            }
        });
    }

    /** A draft made earlier for the same words, model and prompt version, without calling the model. */
    public Optional<HunterSpec> cached(String prompt) {
        return Optional.ofNullable(cache.get(cacheKey(prompt)));
    }

    public Translation translate(String prompt) {
        OpenAiJsonClient.Completion completion = client.complete(new OpenAiJsonClient.Request(
                settings.model(), systemPrompt(), userContent(prompt), "hunter_spec", SCHEMA,
                settings.maxOutputTokens(), settings.reasoningEffort()));

        Reply reply;
        try {
            reply = JSON.readValue(completion.content(), Reply.class);
        } catch (IOException e) {
            throw new ModelCallException("The model's draft was not valid JSON", e);
        }
        HunterSpec spec = toSpec(prompt, reply);
        if (!HunterSpecValidator.validate(spec).isEmpty()) {
            throw new ModelCallException("The model's draft is not a valid hunter: " + HunterSpecValidator.validate(spec));
        }
        cache.put(cacheKey(prompt), spec);
        return new Translation(spec, completion);
    }

    /** Assigns rule IDs and drops anything the model got wrong, so a draft always validates. */
    HunterSpec toSpec(String prompt, Reply reply) {
        List<QuantRule> quantRules = new ArrayList<>();
        for (ReplyQuantRule rule : orEmpty(reply.quantRules())) {
            if (rule == null || rule.metric() == null || rule.op() == null || rule.value() == null) {
                continue;
            }
            BigDecimal value2 = rule.op() == NumericOp.BETWEEN ? rule.value2() : null;
            if (rule.op() == NumericOp.BETWEEN && (value2 == null || value2.compareTo(rule.value()) <= 0)) {
                continue;
            }
            if (quantRules.size() < HunterLimits.MAX_QUANT_RULES) {
                quantRules.add(new QuantRule("q" + (quantRules.size() + 1), true, sourceText(rule.sourceText()),
                        rule.metric(), rule.op(), rule.value(), value2));
            }
        }

        SectorRule sectorRule = null;
        ReplySectorRule replySector = reply.sectorRule();
        if (replySector != null && replySector.op() != null) {
            List<GicsSector> sectors = orEmpty(replySector.values()).stream()
                    .filter(Objects::nonNull).distinct().toList();
            if (!sectors.isEmpty()) {
                sectorRule = new SectorRule("sector", true, sourceText(replySector.sourceText()), replySector.op(), sectors);
            }
        }

        List<Criterion> criteria = new ArrayList<>();
        for (ReplyCriterion criterion : orEmpty(reply.criteria())) {
            if (criterion == null || criterion.statement() == null || criterion.statement().isBlank()
                    || criteria.size() >= HunterLimits.MAX_CRITERIA) {
                continue;
            }
            criteria.add(new Criterion("s" + (criteria.size() + 1), true, sourceText(criterion.sourceText()),
                    truncate(criterion.statement().strip(), HunterLimits.MAX_STATEMENT_LENGTH)));
        }

        List<UnsupportedClause> unsupported = orEmpty(reply.unsupported()).stream()
                .filter(clause -> clause != null && clause.reason() != null)
                .map(clause -> new UnsupportedClause(sourceText(clause.sourceText()), clause.reason(),
                        message(clause.reason(), clause.message())))
                .toList();

        return new HunterSpec(HunterSpec.VERSION, null, name(reply.name()), prompt, HunterSpec.SP500,
                List.copyOf(quantRules), sectorRule, List.copyOf(criteria), unsupported,
                HunterLimits.DEFAULT_CANDIDATES);
    }

    private String message(UnsupportedReason reason, String modelMessage) {
        return switch (reason) {
            case NO_RECENT_DATA -> recentDataMessage();
            case ADVICE_REQUEST -> "Hunter finds companies that match your criteria. It can't say what to buy or sell.";
            default -> modelMessage == null || modelMessage.isBlank() ? "This part could not be turned into a rule."
                    : truncate(modelMessage.strip(), HunterLimits.MAX_STATEMENT_LENGTH);
        };
    }

    private String recentDataMessage() {
        return "The AI's knowledge ends in " + settings.cutoffMonth()
                + ", so recent price moves and events can't be checked.";
    }

    private static String name(String modelName) {
        if (modelName == null || modelName.isBlank() || AdviceGuard.flags(modelName)) {
            return DEFAULT_NAME;
        }
        return truncate(modelName.replaceAll("[\"“”]", "").strip(), HunterLimits.MAX_NAME_LENGTH);
    }

    private static String sourceText(String text) {
        return text == null || text.isBlank() ? null : truncate(text.strip(), HunterLimits.MAX_SOURCE_TEXT_LENGTH);
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max).strip();
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private String cacheKey(String prompt) {
        return settings.model() + "|" + PROMPT_VERSION + "|"
                + prompt.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String userContent(String prompt) {
        try {
            return JSON.writeValueAsString(Map.of("idea", prompt));
        } catch (IOException e) {
            throw new ModelCallException("Could not serialize the prompt", e);
        }
    }

    String systemPrompt() {
        String metrics = Arrays.stream(Metric.values())
                .map(metric -> "- " + metric.id() + ": " + metric.label() + ", " + metric.definition()
                        + " (unit: " + metric.unit() + ")")
                .collect(Collectors.joining("\n"));
        String sectors = Arrays.stream(GicsSector.values()).map(GicsSector::label).collect(Collectors.joining(", "));
        return """
                You turn a retail investor's idea into rules for finding matching S&P 500 companies. You do \
                not pick companies yourself.

                The rules are later judged from an AI model's general knowledge of each company, which ends on \
                %s. There is no live data: no prices, filings or news.

                Numeric metrics you may use, each for the latest reported fiscal period:
                %s
                Market cap is in billions of US dollars. Percentages are in percent units: 50 means 50%%.
                Operators: gt, gte, lt, lte, between. value2 is the upper bound for between and null otherwise.

                GICS sectors you may use, written exactly like this: %s.

                Rules:
                - Use only the metrics and sectors above. Never invent a metric.
                - Use these defaults for vague words:
                  high-margin: grossMarginPct gte 50
                  profitable: operatingMarginPct gt 0
                  fast-growing or high-growth: revenueGrowthPct gte 20
                  cash-generative: fcfMarginPct gte 15
                  cheap or low P/E: peRatio lte 15
                  mega-cap: marketCapUsdB gte 200
                - Use sectorRule only when the idea clearly names one or more GICS sectors ("tech companies" \
                means Information Technology). Otherwise sectorRule is null. Narrower industries, themes, \
                business models and trends ("semiconductors", "AI infrastructure", "founder-led", \
                "subscription software", "improving cash flow") become criteria instead.
                - Write each criterion as one short statement about what the company is or does, as \
                generally known, for example "Sells products or services used to build or run AI data \
                centers". Never about recent events. At most %d criteria.
                - Recent price moves and recent events cannot be checked ("this month", "this week", \
                "recently", "pulled back", "after earnings", "Q3", "this year's guidance", "52-week high"). Put \
                each such phrase in unsupported with reason NO_RECENT_DATA and build no rule from it.
                - A request for advice ("what should I buy", "stocks that will double", "best stocks") goes in \
                unsupported with reason ADVICE_REQUEST. Build no rule from it.
                - Companies outside the S&P 500 (small caps, foreign listings, ETFs, crypto) go in unsupported \
                with reason OUT_OF_UNIVERSE.
                - Qualities and trends that general knowledge can judge ("improving cash flow", "low debt", \
                "a strong brand", "recurring revenue") are criteria, never NO_DATA. Use NO_DATA only for \
                things that need data you cannot have ("insider buying", "analyst upgrades", "short \
                interest"), and AMBIGUOUS for anything too vague to become a rule. Give a short message \
                saying why.
                - sourceText is the exact phrase of the idea that a rule or note came from.
                - name is a short neutral title for the idea, at most 6 words, never words such as buy, best \
                or top picks.
                """.formatted(settings.cutoffDate(), metrics, sectors, HunterLimits.MAX_CRITERIA);
    }

    private static Map<String, Object> object(Map<String, Object> properties) {
        return Map.of("type", "object", "additionalProperties", false,
                "required", List.copyOf(properties.keySet()), "properties", properties);
    }

    private static Map<String, Object> enumOf(List<String> values) {
        return Map.of("type", "string", "enum", values);
    }

    private static final Map<String, Object> STRING = Map.of("type", "string");

    private static final Map<String, Object> SCHEMA = object(orderedMap(
            "name", STRING,
            "quantRules", Map.of("type", "array", "items", object(orderedMap(
                    "metric", enumOf(Arrays.stream(Metric.values()).map(Metric::id).toList()),
                    "op", enumOf(Arrays.stream(NumericOp.values()).map(NumericOp::id).toList()),
                    "value", Map.of("type", "number"),
                    "value2", Map.of("type", List.of("number", "null")),
                    "sourceText", STRING))),
            "sectorRule", Map.of("anyOf", List.of(
                    object(orderedMap(
                            "op", enumOf(Arrays.stream(SectorOp.values()).map(SectorOp::id).toList()),
                            "values", Map.of("type", "array",
                                    "items", enumOf(Arrays.stream(GicsSector.values()).map(GicsSector::label).toList())),
                            "sourceText", STRING)),
                    Map.of("type", "null"))),
            "criteria", Map.of("type", "array", "items", object(orderedMap(
                    "statement", STRING,
                    "sourceText", STRING))),
            "unsupported", Map.of("type", "array", "items", object(orderedMap(
                    "sourceText", STRING,
                    "reason", enumOf(Arrays.stream(UnsupportedReason.values()).map(Enum::name).toList()),
                    "message", STRING)))));

    private static Map<String, Object> orderedMap(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
