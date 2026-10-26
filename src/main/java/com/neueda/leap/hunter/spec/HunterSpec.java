package com.neueda.leap.hunter.spec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.neueda.leap.hunter.catalog.GicsSector;
import com.neueda.leap.hunter.catalog.Metric;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The card of rules a prompt is turned into (docs/specs/watchlist-hunter.md, section 4.1).
 * Missing lists read as empty; anything else wrong is left for HunterSpecValidator to report.
 */
public record HunterSpec(
        int specVersion,
        UUID id,
        String name,
        String prompt,
        String universe,
        List<QuantRule> quantRules,
        SectorRule sectorRule,
        List<Criterion> criteria,
        List<UnsupportedClause> unsupported,
        int candidateLimit) {

    public static final int VERSION = 1;
    public static final String SP500 = "SP500";

    public HunterSpec {
        quantRules = quantRules == null ? List.of() : quantRules;
        criteria = criteria == null ? List.of() : criteria;
        unsupported = unsupported == null ? List.of() : unsupported;
    }

    public HunterSpec withId(UUID newId) {
        return new HunterSpec(specVersion, newId, name, prompt, universe, quantRules, sectorRule, criteria,
                unsupported, candidateLimit);
    }

    public Stream<QuantRule> enabledQuantRules() {
        return quantRules.stream().filter(QuantRule::enabled);
    }

    public Stream<Criterion> enabledCriteria() {
        return criteria.stream().filter(Criterion::enabled);
    }

    public boolean sectorRuleEnabled() {
        return sectorRule != null && sectorRule.enabled();
    }

    public record QuantRule(
            String id,
            boolean enabled,
            String sourceText,
            Metric metric,
            NumericOp op,
            BigDecimal value,
            BigDecimal value2) {

        /** Whether an estimate meets this rule; an unknown estimate never does. */
        public boolean passes(BigDecimal estimate) {
            return estimate != null && op.test(estimate, value, value2);
        }
    }

    public record SectorRule(
            String id,
            boolean enabled,
            String sourceText,
            SectorOp op,
            List<GicsSector> values) {

        public SectorRule {
            values = values == null ? List.of() : values;
        }

        public boolean passes(GicsSector sector) {
            return values.contains(sector) == (op == SectorOp.IN);
        }
    }

    public record Criterion(
            String id,
            boolean enabled,
            String sourceText,
            String statement) {}

    public record UnsupportedClause(
            String sourceText,
            UnsupportedReason reason,
            String message) {}

    public enum NumericOp {
        GT("gt", "greater than"),
        GTE("gte", "at least"),
        LT("lt", "less than"),
        LTE("lte", "at most"),
        BETWEEN("between", "between");

        private final String id;
        private final String words;

        NumericOp(String id, String words) {
            this.id = id;
            this.words = words;
        }

        @JsonValue
        public String id() {
            return id;
        }

        /** "at least 50", "between 10 and 20": how the condition is put to the model. */
        public String describe(BigDecimal value, BigDecimal value2) {
            return this == BETWEEN
                    ? words + " " + value.toPlainString() + " and " + value2.toPlainString()
                    : words + " " + value.toPlainString();
        }

        public boolean test(BigDecimal estimate, BigDecimal value, BigDecimal value2) {
            return switch (this) {
                case GT -> estimate.compareTo(value) > 0;
                case GTE -> estimate.compareTo(value) >= 0;
                case LT -> estimate.compareTo(value) < 0;
                case LTE -> estimate.compareTo(value) <= 0;
                case BETWEEN -> estimate.compareTo(value) >= 0 && estimate.compareTo(value2) <= 0;
            };
        }

        @JsonCreator
        public static NumericOp fromId(String id) {
            return Arrays.stream(values()).filter(op -> op.id.equals(id)).findFirst().orElse(null);
        }
    }

    public enum SectorOp {
        IN("in"),
        NOT_IN("notIn");

        private final String id;

        SectorOp(String id) {
            this.id = id;
        }

        @JsonValue
        public String id() {
            return id;
        }

        @JsonCreator
        public static SectorOp fromId(String id) {
            return Arrays.stream(values()).filter(op -> op.id.equals(id)).findFirst().orElse(null);
        }
    }

    public enum UnsupportedReason {
        NO_RECENT_DATA, NO_DATA, AMBIGUOUS, ADVICE_REQUEST, OUT_OF_UNIVERSE;

        @JsonCreator
        public static UnsupportedReason fromName(String name) {
            return Arrays.stream(values()).filter(reason -> reason.name().equals(name)).findFirst().orElse(null);
        }
    }
}
