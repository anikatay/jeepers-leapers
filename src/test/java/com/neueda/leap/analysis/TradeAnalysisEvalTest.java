package com.neueda.leap.analysis;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the frozen cases in src/test/resources/analysis-eval against the real API, several times
 * each, and checks every reply against the case's expectations and the prompt's rules.
 * Skipped unless OPENAI_API_KEY is set; OPENAI_MODEL picks the model, EVAL_RUNS the repeats (default 3).
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class TradeAnalysisEvalTest {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /**
     * The prompt forbids judging from anything outside the JSON: market, news, timing, sectors. Trend
     * and volatility are banned only as market terms, since they can fairly describe the investor's trading.
     */
    private static final List<String> ALWAYS_BANNED = List.of(
            "market condition", "market trend", "price trend", "market volatil", "volatile stock",
            "volatile sector", "market event", "news", "earnings", "analyst", "stop-loss", "stop loss",
            "technical analysis", "sector", "economy", "economic", "momentum");

    /** gpt-4o-mini called 61% cash "relatively small"; when cash is plentiful it must not say otherwise. */
    private static final List<String> LOW_CASH_CLAIMS = List.of(
            "limited cash", "low cash", "little cash", "small cash", "cash is low", "cash is limited",
            "small remaining cash", "limited flexibility", "limited liquidity");
    private static final BigDecimal PLENTIFUL_CASH_PCT = new BigDecimal("40");

    record Expect(List<String> verdicts, List<String> mustMention, List<String> mustNotMention) {}

    record EvalCase(String name, String source, Expect expect, TradeAnalysisContext context) {
        @Override
        public String toString() {
            return name;
        }
    }

    private record RunSummary(String caseName, int runs, int passed, int promptTokens, int completionTokens,
            long latencyMs, List<String> verdicts) {}

    private static final List<RunSummary> SUMMARIES = new ArrayList<>();
    private static String modelUsed = "";

    static Stream<EvalCase> cases() throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:analysis-eval/*.json");
        List<EvalCase> cases = new ArrayList<>();
        for (Resource file : Arrays.stream(files).sorted(Comparator.comparing(Resource::getFilename)).toList()) {
            try (InputStream in = file.getInputStream()) {
                cases.add(JSON.readValue(in, EvalCase.class));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void meetsExpectations(EvalCase evalCase) {
        String model = envOr("OPENAI_MODEL", "gpt-4o-mini");
        int runs = Integer.parseInt(envOr("EVAL_RUNS", "3"));
        OpenAiTradeAnalyzer analyzer = new OpenAiTradeAnalyzer(
                System.getenv("OPENAI_API_KEY"), model, OpenAiTradeAnalyzer.DEFAULT_BASE_URL);

        List<String> failures = new ArrayList<>();
        List<String> verdicts = new ArrayList<>();
        int passed = 0;
        int promptTokens = 0;
        int completionTokens = 0;
        long latencyMs = 0;
        for (int run = 1; run <= runs; run++) {
            OpenAiTradeAnalyzer.Analysis analysis = analyzer.analyzeWithUsage(evalCase.context());
            modelUsed = analysis.model();
            promptTokens += analysis.promptTokens();
            completionTokens += analysis.completionTokens();
            latencyMs += analysis.latencyMs();
            TradeAnalysisResult result = analysis.result();
            verdicts.add(result.verdict().name());

            List<String> problems = check(evalCase, result);
            System.out.printf("%n--- %s, run %d: %s%s%n%s%n", evalCase.name(), run, result.verdict(),
                    problems.isEmpty() ? "" : "  FAILED: " + problems, render(result));
            if (problems.isEmpty()) {
                passed++;
            } else {
                failures.add("run " + run + ": " + problems);
            }
        }
        SUMMARIES.add(new RunSummary(evalCase.name(), runs, passed, promptTokens / runs, completionTokens / runs,
                latencyMs / runs, verdicts));

        assertTrue(failures.isEmpty(), evalCase.name() + " -> " + failures);
    }

    @AfterAll
    static void printSummary() {
        System.out.printf("%n===== evaluation summary, model %s =====%n", modelUsed);
        System.out.printf("%-55s %6s %-28s %8s %8s %8s%n", "case", "passed", "verdicts", "in tok", "out tok", "ms");
        int casesPassed = 0;
        for (RunSummary s : SUMMARIES) {
            if (s.passed() == s.runs()) {
                casesPassed++;
            }
            System.out.printf("%-55s %3d/%-2d %-28s %8d %8d %8d%n", s.caseName(), s.passed(), s.runs(),
                    String.join(",", s.verdicts()), s.promptTokens(), s.completionTokens(), s.latencyMs());
        }
        System.out.printf("cases passing every run: %d of %d%n", casesPassed, SUMMARIES.size());
    }

    private static List<String> check(EvalCase evalCase, TradeAnalysisResult result) {
        List<String> problems = new ArrayList<>();
        Expect expect = evalCase.expect();
        if (!expect.verdicts().contains(result.verdict().name())) {
            problems.add("verdict " + result.verdict() + " not in " + expect.verdicts());
        }
        if (result.summary().isBlank()) {
            problems.add("empty summary");
        }
        for (List<String> items : List.of(result.strengths(), result.concerns(), result.suggestions())) {
            if (items.size() > 3) {
                problems.add("more than three items in a list");
            }
        }

        String text = String.join(" ", result.summary(), String.join(" ", result.strengths()),
                String.join(" ", result.concerns()), String.join(" ", result.suggestions())).toLowerCase(Locale.ROOT);
        List<String> banned = new ArrayList<>(ALWAYS_BANNED);
        banned.addAll(orEmpty(expect.mustNotMention()));
        if (cashPct(evalCase.context()).compareTo(PLENTIFUL_CASH_PCT) >= 0) {
            banned.addAll(LOW_CASH_CLAIMS);
        }
        for (String phrase : banned) {
            if (mentions(text, phrase)) {
                problems.add("mentions \"" + phrase + "\"");
            }
        }
        for (String phrase : orEmpty(expect.mustMention())) {
            if (!mentions(text, phrase)) {
                problems.add("does not mention \"" + phrase + "\"");
            }
        }
        return problems;
    }

    private static boolean mentions(String text, String phrase) {
        return Pattern.compile("\\b" + Pattern.quote(phrase.toLowerCase(Locale.ROOT))).matcher(text).find();
    }

    private static BigDecimal cashPct(TradeAnalysisContext context) {
        TradeAnalysisContext.Portfolio portfolio = context.portfolio();
        return portfolio.cash().multiply(new BigDecimal("100"))
                .divide(portfolio.totalValue(), 2, RoundingMode.HALF_UP);
    }

    private static String render(TradeAnalysisResult result) {
        return "  summary:     " + result.summary()
                + "\n  strengths:   " + result.strengths()
                + "\n  concerns:    " + result.concerns()
                + "\n  suggestions: " + result.suggestions();
    }

    private static List<String> orEmpty(List<String> list) {
        return list == null ? List.of() : list;
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
