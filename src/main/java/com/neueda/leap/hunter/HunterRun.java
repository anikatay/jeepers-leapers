package com.neueda.leap.hunter;

import com.neueda.leap.hunter.catalog.GicsSector;
import com.neueda.leap.hunter.catalog.Metric;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One saved run of a spec (section 6.1 of the spec). reused and each match's onWatchlist describe
 * the moment it is returned, so they are set on the way out rather than stored.
 */
public record HunterRun(
        UUID runId,
        UUID specId,
        OffsetDateTime ranAt,
        Source source,
        List<Match> matches,
        int droppedCount,
        String disclaimer,
        boolean reused) {

    public static final String DISCLAIMER = "These companies match the criteria you set, according to the AI's "
            + "general knowledge up to %s. Nothing here has been checked against filings or market data, and "
            + "figures may be out of date. This is for research and education, not a recommendation to buy, "
            + "sell or hold.";

    public record Source(String model, LocalDate knowledgeCutoff) {}

    public record Match(
            String ticker,
            String companyName,
            GicsSector gicsSector,
            Integer matchScore,
            List<String> whyItMatched,
            List<CriterionResult> criteria,
            List<EstimateValue> estimates,
            boolean onWatchlist) {

        Match withOnWatchlist(boolean watched) {
            return new Match(ticker, companyName, gicsSector, matchScore, whyItMatched, criteria, estimates, watched);
        }
    }

    public record CriterionResult(String criterionId, CheckVerdict verdict, String reason) {}

    /** The model's estimate for one enabled numeric rule, for the period it names. */
    public record EstimateValue(String ruleId, Metric metric, BigDecimal value, String period) {}

    public HunterRun asReturned(boolean wasReused, Set<String> watchlistTickers) {
        return new HunterRun(runId, specId, ranAt, source,
                matches.stream().map(match -> match.withOnWatchlist(watchlistTickers.contains(match.ticker()))).toList(),
                droppedCount, disclaimer, wasReused);
    }
}
