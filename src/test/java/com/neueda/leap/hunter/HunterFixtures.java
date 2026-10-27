package com.neueda.leap.hunter;

import com.neueda.leap.hunter.catalog.GicsSector;
import com.neueda.leap.hunter.catalog.Metric;
import com.neueda.leap.hunter.spec.HunterSpec;
import com.neueda.leap.hunter.spec.HunterSpec.Criterion;
import com.neueda.leap.hunter.spec.HunterSpec.NumericOp;
import com.neueda.leap.hunter.spec.HunterSpec.QuantRule;
import com.neueda.leap.hunter.spec.HunterSpec.SectorOp;
import com.neueda.leap.hunter.spec.HunterSpec.SectorRule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Specs and settings shared by the Hunter tests. */
public final class HunterFixtures {

    public static final HunterModelSettings SETTINGS =
            new HunterModelSettings("test-model", LocalDate.parse("2026-05-18"), "low", 8000);

    private HunterFixtures() {}

    public static QuantRule grossMarginAtLeast(String id, String value) {
        return new QuantRule(id, true, "high-margin", Metric.GROSS_MARGIN_PCT, NumericOp.GTE,
                new BigDecimal(value), null);
    }

    public static QuantRule peBetween(String id, String low, String high) {
        return new QuantRule(id, true, null, Metric.PE_RATIO, NumericOp.BETWEEN, new BigDecimal(low),
                new BigDecimal(high));
    }

    public static SectorRule sectors(GicsSector... values) {
        return new SectorRule("sector", true, "tech companies", SectorOp.IN, List.of(values));
    }

    public static Criterion criterion(String id, String statement) {
        return new Criterion(id, true, "AI infrastructure", statement);
    }

    public static HunterSpec spec(List<QuantRule> quantRules, SectorRule sectorRule, List<Criterion> criteria) {
        return new HunterSpec(HunterSpec.VERSION, null, "AI infrastructure", "Find high-margin AI infrastructure stocks",
                HunterSpec.SP500, quantRules, sectorRule, criteria, List.of(), HunterLimits.DEFAULT_CANDIDATES);
    }

    /** High-margin AI infrastructure: one numeric rule and one check, as in section 4.3 of the spec. */
    public static HunterSpec aiInfrastructure() {
        return spec(List.of(grossMarginAtLeast("q1", "50")), null,
                List.of(criterion("s1", "Sells products or services used to build or run AI data centers")));
    }
}
