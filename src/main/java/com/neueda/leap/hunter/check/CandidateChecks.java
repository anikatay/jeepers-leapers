package com.neueda.leap.hunter.check;

import com.neueda.leap.hunter.CheckVerdict;
import com.neueda.leap.hunter.HunterRun.CriterionResult;
import com.neueda.leap.hunter.HunterRun.EstimateValue;
import com.neueda.leap.hunter.catalog.Universe;
import com.neueda.leap.hunter.find.CandidateFinder.Candidate;
import com.neueda.leap.hunter.find.CandidateFinder.Estimate;
import com.neueda.leap.hunter.find.CandidateFinder.Judgement;
import com.neueda.leap.hunter.spec.HunterSpec;
import com.neueda.leap.hunter.spec.HunterSpec.Criterion;
import com.neueda.leap.hunter.spec.HunterSpec.QuantRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The checks in code from section 5.3 of the spec. The model's word is never taken for which
 * companies are in the index or whether its own estimates meet the thresholds.
 */
public final class CandidateChecks {

    /** A candidate that passed, with the model's rank so ties keep its order. */
    public record Checked(
            Universe.Company company,
            int modelRank,
            List<EstimateValue> estimates,
            List<CriterionResult> criteria,
            List<String> bullets) {}

    public record Outcome(
            List<Checked> kept,
            int droppedNotInUniverse,
            int droppedByRules,
            int sourceGuardRemovals,
            int adviceGuardRemovals) {

        public int droppedCount() {
            return droppedNotInUniverse + droppedByRules;
        }
    }

    private CandidateChecks() {}

    public static Outcome apply(HunterSpec spec, List<Candidate> candidates, Universe universe) {
        List<QuantRule> rules = spec.enabledQuantRules().toList();
        List<Criterion> criteria = spec.enabledCriteria().toList();
        GuardCounts guards = new GuardCounts();

        List<Checked> kept = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int notInUniverse = 0;
        int byRules = 0;
        for (int rank = 0; rank < candidates.size(); rank++) {
            Candidate candidate = candidates.get(rank);
            Optional<Universe.Company> company = candidate == null ? Optional.empty() : universe.find(candidate.ticker());
            if (company.isEmpty()) {
                notInUniverse++;
                continue;
            }
            if (!seen.add(company.get().ticker())) {
                continue;
            }
            if (spec.sectorRuleEnabled() && !spec.sectorRule().passes(company.get().sector())) {
                byRules++;
                continue;
            }

            List<EstimateValue> estimates = new ArrayList<>();
            boolean passes = true;
            for (QuantRule rule : rules) {
                Optional<Estimate> estimate = candidate.estimates().stream()
                        .filter(e -> e != null && rule.id().equals(e.ruleId())).findFirst();
                if (estimate.isEmpty() || !rule.passes(estimate.get().value())) {
                    passes = false;
                    break;
                }
                String period = estimate.get().period();
                estimates.add(new EstimateValue(rule.id(), rule.metric(), estimate.get().value(),
                        period == null || period.isBlank() || guards.flagged(period) ? null : period.strip()));
            }
            if (!passes) {
                byRules++;
                continue;
            }

            List<CriterionResult> results = criteria.stream()
                    .map(criterion -> judge(criterion, candidate.criteria(), guards))
                    .toList();
            List<String> bullets = candidate.whyItMatched().stream()
                    .filter(bullet -> bullet != null && !bullet.isBlank())
                    .map(String::strip)
                    .filter(bullet -> !guards.flagged(bullet))
                    .toList();
            kept.add(new Checked(company.get(), rank, List.copyOf(estimates), results, bullets));
        }
        return new Outcome(kept, notInUniverse, byRules, guards.source, guards.advice);
    }

    /** A check the model left out counts as UNKNOWN; a flagged reason is removed but its verdict kept. */
    private static CriterionResult judge(Criterion criterion, List<Judgement> judgements, GuardCounts guards) {
        Optional<Judgement> judgement = judgements.stream()
                .filter(j -> j != null && criterion.id().equals(j.criterionId()) && j.verdict() != null)
                .findFirst();
        if (judgement.isEmpty()) {
            return new CriterionResult(criterion.id(), CheckVerdict.UNKNOWN, null);
        }
        String reason = judgement.get().reason();
        if (reason == null || reason.isBlank() || guards.flagged(reason)) {
            reason = null;
        }
        return new CriterionResult(criterion.id(), judgement.get().verdict(), reason == null ? null : reason.strip());
    }

    private static final class GuardCounts {
        private int source;
        private int advice;

        boolean flagged(String text) {
            if (SourceGuard.flags(text)) {
                source++;
                return true;
            }
            if (AdviceGuard.flags(text)) {
                advice++;
                return true;
            }
            return false;
        }
    }
}
