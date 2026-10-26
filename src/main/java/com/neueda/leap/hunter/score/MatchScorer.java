package com.neueda.leap.hunter.score;

import com.neueda.leap.hunter.HunterLimits;
import com.neueda.leap.hunter.HunterRun.CriterionResult;
import com.neueda.leap.hunter.HunterRun.EstimateValue;
import com.neueda.leap.hunter.HunterRun.Match;
import com.neueda.leap.hunter.catalog.Metric;
import com.neueda.leap.hunter.check.CandidateChecks.Checked;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Score, reasons and order (section 5.4 of the spec). The score comes from the verdicts in code;
 * the model never produces a number for it.
 */
public final class MatchScorer {

    private MatchScorer() {}

    /** MET 1, PARTIAL 0.5, anything else 0, averaged and rounded to 0–100; null without checks. */
    public static Integer score(List<CriterionResult> results) {
        if (results.isEmpty()) {
            return null;
        }
        double points = results.stream().mapToDouble(result -> switch (result.verdict()) {
            case MET -> 1.0;
            case PARTIAL -> 0.5;
            case NOT_MET, UNKNOWN -> 0.0;
        }).sum();
        return (int) Math.round(100 * points / results.size());
    }

    /** The model's surviving bullets first, then fixed-template ones from the estimates, at most 3. */
    public static List<String> whyItMatched(List<String> modelBullets, List<EstimateValue> estimates) {
        List<String> bullets = new ArrayList<>(modelBullets.stream().limit(HunterLimits.MAX_BULLETS).toList());
        for (EstimateValue estimate : estimates) {
            if (bullets.size() >= HunterLimits.MAX_BULLETS) {
                break;
            }
            bullets.add(template(estimate));
        }
        return List.copyOf(bullets);
    }

    /** "Gross margin estimated at about 75% (FY2026)." */
    static String template(EstimateValue estimate) {
        Metric metric = estimate.metric();
        String number = format(estimate.value());
        String amount = switch (metric.unit()) {
            case "%" -> number + "%";
            case "$ bn" -> "$" + number + " bn";
            default -> number + metric.unit();
        };
        String period = estimate.period() == null ? "" : " (" + estimate.period() + ")";
        return metric.label() + " estimated at about " + amount + period + ".";
    }

    /** Matches by score, high to low with nulls last, then in the model's order; the first limit are kept. */
    public static List<Match> rank(List<Checked> checked, int limit) {
        return checked.stream()
                .map(candidate -> new Ranked(candidate, score(candidate.criteria())))
                .sorted(Comparator.comparing(Ranked::score, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparingInt(ranked -> ranked.candidate().modelRank()))
                .limit(limit)
                .map(ranked -> new Match(
                        ranked.candidate().company().ticker(),
                        ranked.candidate().company().name(),
                        ranked.candidate().company().sector(),
                        ranked.score(),
                        whyItMatched(ranked.candidate().bullets(), ranked.candidate().estimates()),
                        ranked.candidate().criteria(),
                        ranked.candidate().estimates(),
                        false))
                .toList();
    }

    private record Ranked(Checked candidate, Integer score) {}

    private static String format(BigDecimal value) {
        DecimalFormat format = new DecimalFormat("#,##0.#", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(value.setScale(1, RoundingMode.HALF_UP));
    }
}
