package com.neueda.leap.hunter.check;

import com.neueda.leap.hunter.CheckVerdict;
import com.neueda.leap.hunter.HunterFixtures;
import com.neueda.leap.hunter.HunterRun.CriterionResult;
import com.neueda.leap.hunter.catalog.GicsSector;
import com.neueda.leap.hunter.catalog.Universe;
import com.neueda.leap.hunter.find.CandidateFinder.Candidate;
import com.neueda.leap.hunter.find.CandidateFinder.Estimate;
import com.neueda.leap.hunter.find.CandidateFinder.Judgement;
import com.neueda.leap.hunter.spec.HunterSpec;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CandidateChecksTest {

    private static final Universe UNIVERSE = new Universe();
    private static final HunterSpec SPEC = HunterFixtures.aiInfrastructure();

    @Test
    void dropsTickersOutsideTheUniverseAndDuplicates() {
        CandidateChecks.Outcome outcome = CandidateChecks.apply(SPEC, List.of(
                candidate("NVDA", "75"),
                candidate("MADEUP", "90"),
                candidate("nvda", "75"),
                candidate("TSM", "95")), UNIVERSE);

        assertEquals(List.of("NVDA"), tickers(outcome));
        assertEquals(2, outcome.droppedNotInUniverse());
        assertEquals(2, outcome.droppedCount());
    }

    @Test
    void checksThresholdsAgainstTheModelsOwnEstimates() {
        CandidateChecks.Outcome outcome = CandidateChecks.apply(SPEC, List.of(
                candidate("NVDA", "75"),
                candidate("AVGO", "45"),
                candidate("ANET", "50"),
                new Candidate("AMD", List.of(new Estimate("q1", null, "FY2025")), List.of(), List.of()),
                new Candidate("MU", List.of(), List.of(), List.of())), UNIVERSE);

        assertEquals(List.of("NVDA", "ANET"), tickers(outcome));
        assertEquals(3, outcome.droppedByRules());
    }

    @Test
    void checksBetweenInclusively() {
        HunterSpec spec = HunterFixtures.spec(List.of(HunterFixtures.peBetween("q1", "10", "20")), null, List.of());

        CandidateChecks.Outcome outcome = CandidateChecks.apply(spec, List.of(
                candidate("JPM", "10"), candidate("BAC", "20"), candidate("WFC", "20.5"), candidate("C", "9.9")), UNIVERSE);

        assertEquals(List.of("JPM", "BAC"), tickers(outcome));
    }

    @Test
    void usesTheListsSectorNotTheModels() {
        HunterSpec spec = HunterFixtures.spec(List.of(), HunterFixtures.sectors(GicsSector.INFORMATION_TECHNOLOGY),
                List.of());

        CandidateChecks.Outcome outcome = CandidateChecks.apply(spec, List.of(
                new Candidate("NVDA", List.of(), List.of(), List.of()),
                new Candidate("GOOGL", List.of(), List.of(), List.of())), UNIVERSE);

        assertEquals(List.of("NVDA"), tickers(outcome));
        assertEquals(1, outcome.droppedByRules());
    }

    @Test
    void ignoresDisabledRules() {
        HunterSpec spec = HunterFixtures.spec(
                List.of(new HunterSpec.QuantRule("q1", false, null, com.neueda.leap.hunter.catalog.Metric.GROSS_MARGIN_PCT,
                        HunterSpec.NumericOp.GTE, new BigDecimal("90"), null)),
                null, List.of());

        CandidateChecks.Outcome outcome = CandidateChecks.apply(spec, List.of(candidate("NVDA", "75")), UNIVERSE);

        assertEquals(List.of("NVDA"), tickers(outcome));
        assertEquals(List.of(), outcome.kept().get(0).estimates());
    }

    @Test
    void removesInventedSourcesAndAdviceButKeepsTheVerdict() {
        Candidate nvda = new Candidate("NVDA", List.of(new Estimate("q1", new BigDecimal("75"), "FY2026 10-K")),
                List.of(new Judgement("s1", CheckVerdict.MET, "Its 10-K says \"AI demand is strong\".")),
                List.of("Designs GPUs used in AI data centers.", "The stock looks undervalued.",
                        "On its earnings call it raised guidance.", " "));

        CandidateChecks.Outcome outcome = CandidateChecks.apply(SPEC, List.of(nvda), UNIVERSE);

        CandidateChecks.Checked kept = outcome.kept().get(0);
        assertEquals(new CriterionResult("s1", CheckVerdict.MET, null), kept.criteria().get(0));
        assertEquals(List.of("Designs GPUs used in AI data centers."), kept.bullets());
        assertNull(kept.estimates().get(0).period());
        assertEquals(3, outcome.sourceGuardRemovals());
        assertEquals(1, outcome.adviceGuardRemovals());
    }

    @Test
    void countsAMissingVerdictAsUnknown() {
        CandidateChecks.Outcome outcome = CandidateChecks.apply(SPEC, List.of(candidate("NVDA", "75")), UNIVERSE);

        assertEquals(new CriterionResult("s1", CheckVerdict.UNKNOWN, null), outcome.kept().get(0).criteria().get(0));
    }

    private static Candidate candidate(String ticker, String grossMargin) {
        return new Candidate(ticker, List.of(new Estimate("q1", new BigDecimal(grossMargin), "FY2025")), List.of(),
                List.of());
    }

    private static List<String> tickers(CandidateChecks.Outcome outcome) {
        return outcome.kept().stream().map(checked -> checked.company().ticker()).toList();
    }
}
