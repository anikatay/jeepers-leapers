package com.neueda.leap.hunter.score;

import com.neueda.leap.hunter.CheckVerdict;
import com.neueda.leap.hunter.HunterRun.CriterionResult;
import com.neueda.leap.hunter.HunterRun.EstimateValue;
import com.neueda.leap.hunter.HunterRun.Match;
import com.neueda.leap.hunter.catalog.GicsSector;
import com.neueda.leap.hunter.catalog.Metric;
import com.neueda.leap.hunter.catalog.Universe;
import com.neueda.leap.hunter.check.CandidateChecks.Checked;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MatchScorerTest {

    @Test
    void scoresTwoMetAndOnePartialAs83() {
        assertEquals(83, MatchScorer.score(List.of(result(CheckVerdict.MET), result(CheckVerdict.MET),
                result(CheckVerdict.PARTIAL))));
    }

    @Test
    void scoresNotMetAndUnknownAsZero() {
        assertEquals(0, MatchScorer.score(List.of(result(CheckVerdict.NOT_MET), result(CheckVerdict.UNKNOWN))));
        assertEquals(100, MatchScorer.score(List.of(result(CheckVerdict.MET))));
    }

    @Test
    void hasNoScoreWithoutChecks() {
        assertNull(MatchScorer.score(List.of()));
    }

    @Test
    void fillsReasonsFromTheEstimatesAfterTheModelsOwn() {
        List<EstimateValue> estimates = List.of(
                new EstimateValue("q1", Metric.GROSS_MARGIN_PCT, new BigDecimal("75.04"), "FY2026"),
                new EstimateValue("q2", Metric.MARKET_CAP_USD_B, new BigDecimal("4300"), null),
                new EstimateValue("q3", Metric.PE_RATIO, new BigDecimal("28"), "FY2025"));

        assertEquals(List.of("Designs GPUs for AI.", "Gross margin estimated at about 75% (FY2026).",
                        "Market cap estimated at about $4,300 bn."),
                MatchScorer.whyItMatched(List.of("Designs GPUs for AI."), estimates));
        assertEquals("P/E estimated at about 28× (FY2025).", MatchScorer.template(estimates.get(2)));
        assertEquals(3, MatchScorer.whyItMatched(List.of("a", "b", "c", "d"), estimates).size());
    }

    @Test
    void ordersByScoreThenTheModelsOrderAndKeepsTheLimit() {
        List<Checked> checked = List.of(
                checked("AAA", 0, CheckVerdict.PARTIAL),
                checked("BBB", 1, CheckVerdict.MET),
                checked("CCC", 2, CheckVerdict.PARTIAL),
                checked("DDD", 3, CheckVerdict.NOT_MET));

        List<Match> ranked = MatchScorer.rank(checked, 3);

        assertEquals(List.of("BBB", "AAA", "CCC"), ranked.stream().map(Match::ticker).toList());
        assertEquals(List.of(100, 50, 50), ranked.stream().map(Match::matchScore).toList());
    }

    private static CriterionResult result(CheckVerdict verdict) {
        return new CriterionResult("s1", verdict, null);
    }

    private static Checked checked(String ticker, int rank, CheckVerdict verdict) {
        return new Checked(new Universe.Company(ticker, ticker + " Inc.", GicsSector.INFORMATION_TECHNOLOGY), rank,
                List.of(), List.of(result(verdict)), List.of());
    }
}
