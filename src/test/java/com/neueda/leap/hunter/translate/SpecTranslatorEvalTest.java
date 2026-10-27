package com.neueda.leap.hunter.translate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.neueda.leap.ai.OpenAiJsonClient;
import com.neueda.leap.hunter.HunterModelSettings;
import com.neueda.leap.hunter.check.AdviceGuard;
import com.neueda.leap.hunter.spec.HunterSpec;
import com.neueda.leap.hunter.spec.HunterSpecValidator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the frozen prompts in src/test/resources/hunter-eval/translator-cases.json against the real
 * API (section 9 of docs/specs/watchlist-hunter.md), several times each. Skipped unless
 * OPENAI_API_KEY is set; HUNTER_OPENAI_MODEL picks the model, EVAL_RUNS the repeats (default 3).
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class SpecTranslatorEvalTest {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    record ExpectedRule(String metric, List<String> ops) {}

    record ExpectedSectors(String op, List<String> values) {}

    record Expect(List<ExpectedRule> metrics, ExpectedSectors sectors, List<String> reasons, Integer minCriteria,
            Boolean noRules) {}

    record EvalCase(String name, String prompt, Expect expect) {
        @Override
        public String toString() {
            return name;
        }
    }

    private record Summary(String name, int runs, int passed, int promptTokens, int completionTokens, long latencyMs) {}

    private static final List<Summary> SUMMARIES = new ArrayList<>();
    private static String modelUsed = "";

    static Stream<EvalCase> cases() throws IOException {
        try (InputStream in = new ClassPathResource("hunter-eval/translator-cases.json").getInputStream()) {
            return JSON.readValue(in, new TypeReference<List<EvalCase>>() {}).stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void meetsExpectations(EvalCase evalCase) {
        int runs = Integer.parseInt(envOr("EVAL_RUNS", "3"));
        // A cache of 0 entries keeps nothing, so every run asks the model again
        SpecTranslator translator = new SpecTranslator(client(), settings(), 0);

        List<String> failures = new ArrayList<>();
        int passed = 0;
        int promptTokens = 0;
        int completionTokens = 0;
        long latencyMs = 0;
        for (int run = 1; run <= runs; run++) {
            SpecTranslator.Translation translation = translator.translate(evalCase.prompt());
            modelUsed = translation.completion().model();
            promptTokens += translation.completion().promptTokens();
            completionTokens += translation.completion().completionTokens();
            latencyMs += translation.completion().latencyMs();

            List<String> problems = check(translation.spec(), evalCase.expect());
            System.out.printf("%n--- %s, run %d%s%n%s%n", evalCase.name(), run,
                    problems.isEmpty() ? "" : "  FAILED: " + problems, render(translation.spec()));
            if (problems.isEmpty()) {
                passed++;
            } else {
                failures.add("run " + run + ": " + problems);
            }
        }
        SUMMARIES.add(new Summary(evalCase.name(), runs, passed, promptTokens / runs, completionTokens / runs,
                latencyMs / runs));

        assertTrue(failures.isEmpty(), evalCase.name() + " -> " + failures);
    }

    @AfterAll
    static void printSummary() {
        System.out.printf("%n===== translator evaluation, model %s =====%n", modelUsed);
        int casesPassed = 0;
        for (Summary s : SUMMARIES) {
            casesPassed += s.passed() == s.runs() ? 1 : 0;
            System.out.printf("%-60s %3d/%-2d %7d in %6d out %7d ms%n", s.name(), s.passed(), s.runs(),
                    s.promptTokens(), s.completionTokens(), s.latencyMs());
        }
        System.out.printf("cases passing every run: %d of %d%n", casesPassed, SUMMARIES.size());
    }

    static List<String> check(HunterSpec spec, Expect expect) {
        List<String> problems = new ArrayList<>();
        if (!HunterSpecValidator.validate(spec).isEmpty()) {
            problems.add("invalid spec: " + HunterSpecValidator.validate(spec));
        }
        if (AdviceGuard.flags(spec.name())) {
            problems.add("advice in the name");
        }
        for (ExpectedRule expected : orEmpty(expect.metrics())) {
            boolean found = spec.quantRules().stream().anyMatch(rule -> rule.metric() != null
                    && rule.metric().id().equals(expected.metric()) && expected.ops().contains(rule.op().id()));
            if (!found) {
                problems.add("no " + expected.metric() + " rule with " + expected.ops());
            }
        }
        if (expect.sectors() != null) {
            Set<String> actual = spec.sectorRule() == null ? Set.of()
                    : spec.sectorRule().values().stream().map(sector -> sector.label()).collect(Collectors.toSet());
            if (spec.sectorRule() == null || !spec.sectorRule().op().id().equals(expect.sectors().op())
                    || !actual.equals(new HashSet<>(expect.sectors().values()))) {
                problems.add("sector rule " + (spec.sectorRule() == null ? "missing"
                        : spec.sectorRule().op().id() + " " + actual) + ", expected " + expect.sectors());
            }
        }
        Set<String> reasons = spec.unsupported().stream().map(clause -> clause.reason().name()).collect(Collectors.toSet());
        for (String reason : orEmpty(expect.reasons())) {
            if (!reasons.contains(reason)) {
                problems.add("no " + reason + " clause (got " + reasons + ")");
            }
        }
        if (expect.minCriteria() != null && spec.criteria().size() < expect.minCriteria()) {
            problems.add("fewer than " + expect.minCriteria() + " criteria");
        }
        if (Boolean.TRUE.equals(expect.noRules())
                && (!spec.quantRules().isEmpty() || spec.sectorRule() != null || !spec.criteria().isEmpty())) {
            problems.add("built rules from a request that should give none");
        }
        return problems;
    }

    private static String render(HunterSpec spec) {
        StringBuilder text = new StringBuilder("  name: ").append(spec.name());
        spec.quantRules().forEach(rule -> text.append("\n  ").append(rule.id()).append(": ").append(rule.metric().id())
                .append(' ').append(rule.op().id()).append(' ').append(rule.value().toPlainString())
                .append(rule.value2() == null ? "" : " " + rule.value2().toPlainString()));
        if (spec.sectorRule() != null) {
            text.append("\n  sector: ").append(spec.sectorRule().op().id()).append(' ').append(spec.sectorRule().values());
        }
        spec.criteria().forEach(criterion -> text.append("\n  ").append(criterion.id()).append(": ").append(criterion.statement()));
        spec.unsupported().forEach(clause -> text.append("\n  not used: ").append(clause.reason())
                .append(" \"").append(clause.sourceText()).append('"'));
        return text.toString();
    }

    static HunterModelSettings settings() {
        return new HunterModelSettings(envOr("HUNTER_OPENAI_MODEL", "gpt-6-luna"), LocalDate.parse("2026-05-18"), "low", 8000);
    }

    static OpenAiJsonClient client() {
        return new OpenAiJsonClient(System.getenv("OPENAI_API_KEY"), OpenAiJsonClient.DEFAULT_BASE_URL,
                Duration.ofSeconds(120));
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
