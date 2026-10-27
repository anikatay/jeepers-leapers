package com.neueda.leap.hunter.find;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.neueda.leap.ai.OpenAiJsonClient;
import com.neueda.leap.hunter.HunterLimits;
import com.neueda.leap.hunter.HunterModelSettings;
import com.neueda.leap.hunter.catalog.GicsSector;
import com.neueda.leap.hunter.catalog.Metric;
import com.neueda.leap.hunter.catalog.Universe;
import com.neueda.leap.hunter.check.CandidateChecks;
import com.neueda.leap.hunter.spec.HunterSpec;
import com.neueda.leap.hunter.spec.HunterSpec.Criterion;
import com.neueda.leap.hunter.spec.HunterSpec.NumericOp;
import com.neueda.leap.hunter.spec.HunterSpec.QuantRule;
import com.neueda.leap.hunter.spec.HunterSpec.SectorOp;
import com.neueda.leap.hunter.spec.HunterSpec.SectorRule;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the frozen specs in src/test/resources/hunter-eval/finder-cases.json against the real API
 * (section 9 of docs/specs/watchlist-hunter.md), several times each, and checks the model's
 * candidates before the code checks clean them up. Skipped unless OPENAI_API_KEY is set;
 * HUNTER_OPENAI_MODEL picks the model, EVAL_RUNS the repeats (default 3).
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class CandidateFinderEvalTest {

    private static final int MAX_NOT_IN_UNIVERSE = 2;
    private static final Universe UNIVERSE = new Universe();
    private static final ObjectMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    record CaseRule(String id, String metric, String op, BigDecimal value, BigDecimal value2) {}

    record CaseSectors(String op, List<String> values) {}

    record CaseCheck(String id, String statement) {}

    record EvalCase(String name, List<CaseRule> quantRules, CaseSectors sectorRule, List<CaseCheck> criteria,
            List<String> mustNotInclude, int minCandidates) {
        @Override
        public String toString() {
            return name;
        }

        HunterSpec spec() {
            return new HunterSpec(HunterSpec.VERSION, null, name, "", HunterSpec.SP500,
                    orEmpty(quantRules).stream().map(r -> new QuantRule(r.id(), true, null, Metric.fromId(r.metric()),
                            NumericOp.fromId(r.op()), r.value(), r.value2())).toList(),
                    sectorRule == null ? null : new SectorRule("sector", true, null, SectorOp.fromId(sectorRule.op()),
                            sectorRule.values().stream().map(GicsSector::fromLabel).toList()),
                    orEmpty(criteria).stream().map(c -> new Criterion(c.id(), true, null, c.statement())).toList(),
                    List.of(), HunterLimits.DEFAULT_CANDIDATES);
        }
    }

    private record Summary(String name, int runs, int passed, int candidates, int promptTokens, int completionTokens,
            long latencyMs) {}

    private static final List<Summary> SUMMARIES = new ArrayList<>();
    private static String modelUsed = "";

    static Stream<EvalCase> cases() throws IOException {
        try (InputStream in = new ClassPathResource("hunter-eval/finder-cases.json").getInputStream()) {
            return JSON.readValue(in, new TypeReference<List<EvalCase>>() {}).stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void meetsExpectations(EvalCase evalCase) {
        int runs = Integer.parseInt(envOr("EVAL_RUNS", "3"));
        HunterModelSettings settings = new HunterModelSettings(envOr("HUNTER_OPENAI_MODEL", "gpt-6-luna"),
                LocalDate.parse("2026-05-18"), "low", 8000);
        CandidateFinder finder = new CandidateFinder(new OpenAiJsonClient(System.getenv("OPENAI_API_KEY"),
                OpenAiJsonClient.DEFAULT_BASE_URL, Duration.ofSeconds(120)), settings);
        HunterSpec spec = evalCase.spec();

        List<String> failures = new ArrayList<>();
        int passed = 0;
        int candidates = 0;
        int promptTokens = 0;
        int completionTokens = 0;
        long latencyMs = 0;
        for (int run = 1; run <= runs; run++) {
            CandidateFinder.Result result = finder.find(spec);
            modelUsed = result.completion().model();
            promptTokens += result.completion().promptTokens();
            completionTokens += result.completion().completionTokens();
            latencyMs += result.completion().latencyMs();
            CandidateChecks.Outcome outcome = CandidateChecks.apply(spec, result.candidates(), UNIVERSE);
            candidates += outcome.kept().size();

            List<String> problems = check(spec, evalCase, result.candidates(), outcome);
            System.out.printf("%n--- %s, run %d: %d returned, %d kept%s%n  %s%n", evalCase.name(), run,
                    result.candidates().size(), outcome.kept().size(), problems.isEmpty() ? "" : "  FAILED: " + problems,
                    result.candidates().stream().map(CandidateFinder.Candidate::ticker).toList());
            if (problems.isEmpty()) {
                passed++;
            } else {
                failures.add("run " + run + ": " + problems);
            }
        }
        SUMMARIES.add(new Summary(evalCase.name(), runs, passed, candidates / runs, promptTokens / runs,
                completionTokens / runs, latencyMs / runs));

        assertTrue(failures.isEmpty(), evalCase.name() + " -> " + failures);
    }

    @AfterAll
    static void printSummary() {
        System.out.printf("%n===== finder evaluation, model %s =====%n", modelUsed);
        int casesPassed = 0;
        for (Summary s : SUMMARIES) {
            casesPassed += s.passed() == s.runs() ? 1 : 0;
            System.out.printf("%-60s %3d/%-2d %3d kept %7d in %6d out %7d ms%n", s.name(), s.passed(), s.runs(),
                    s.candidates(), s.promptTokens(), s.completionTokens(), s.latencyMs());
        }
        System.out.printf("cases passing every run: %d of %d%n", casesPassed, SUMMARIES.size());
    }

    static List<String> check(HunterSpec spec, EvalCase evalCase, List<CandidateFinder.Candidate> returned,
            CandidateChecks.Outcome outcome) {
        List<String> problems = new ArrayList<>();
        if (outcome.droppedNotInUniverse() > MAX_NOT_IN_UNIVERSE) {
            problems.add(outcome.droppedNotInUniverse() + " tickers outside the S&P 500 list");
        }
        for (CandidateFinder.Candidate candidate : returned) {
            for (QuantRule rule : spec.enabledQuantRules().toList()) {
                candidate.estimates().stream()
                        .filter(e -> rule.id().equals(e.ruleId()) && e.value() != null && !rule.passes(e.value()))
                        .findFirst()
                        .ifPresent(e -> problems.add(candidate.ticker() + " fails " + rule.id() + " by its own estimate "
                                + e.value().toPlainString()));
            }
            if (evalCase.mustNotInclude().contains(Universe.normalize(candidate.ticker()))) {
                problems.add("returned " + candidate.ticker());
            }
        }
        if (outcome.sourceGuardRemovals() > 0) {
            problems.add(outcome.sourceGuardRemovals() + " removals by SourceGuard");
        }
        if (outcome.adviceGuardRemovals() > 0) {
            problems.add(outcome.adviceGuardRemovals() + " removals by AdviceGuard");
        }
        if (outcome.kept().size() < evalCase.minCandidates()) {
            problems.add("only " + outcome.kept().size() + " kept, expected at least " + evalCase.minCandidates());
        }
        return problems;
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
