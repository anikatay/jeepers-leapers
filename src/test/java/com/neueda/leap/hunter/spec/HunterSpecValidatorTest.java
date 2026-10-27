package com.neueda.leap.hunter.spec;

import com.neueda.leap.dto.response.FieldError;
import com.neueda.leap.hunter.HunterFixtures;
import com.neueda.leap.hunter.catalog.Metric;
import com.neueda.leap.hunter.spec.HunterSpec.Criterion;
import com.neueda.leap.hunter.spec.HunterSpec.NumericOp;
import com.neueda.leap.hunter.spec.HunterSpec.QuantRule;
import com.neueda.leap.hunter.spec.HunterSpec.SectorOp;
import com.neueda.leap.hunter.spec.HunterSpec.SectorRule;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HunterSpecValidatorTest {

    @Test
    void acceptsTheExampleSpec() {
        assertTrue(HunterSpecValidator.validate(HunterFixtures.aiInfrastructure()).isEmpty());
    }

    @Test
    void reportsEveryProblemWithItsField() {
        HunterSpec spec = HunterFixtures.spec(
                List.of(new QuantRule("q1", true, null, null, NumericOp.GTE, new BigDecimal("50"), null),
                        new QuantRule("q2", true, null, Metric.PE_RATIO, null, null, null),
                        new QuantRule("q3", true, null, Metric.PE_RATIO, NumericOp.BETWEEN, new BigDecimal("20"),
                                new BigDecimal("10")),
                        new QuantRule("q3", true, null, Metric.PE_RATIO, NumericOp.LT, new BigDecimal("20"),
                                new BigDecimal("30"))),
                new SectorRule("sector", true, null, SectorOp.IN, Collections.singletonList(null)),
                List.of(new Criterion("s1", true, null, " ")));

        List<String> fields = HunterSpecValidator.validate(spec).stream().map(error -> error.field).toList();

        assertEquals(List.of("quantRules[0].metric", "quantRules[1].op", "quantRules[1].value",
                "quantRules[2].value2", "quantRules[3].id", "quantRules[3].value2", "sectorRule.values[0]",
                "criteria[0].statement"), fields);
    }

    @Test
    void enforcesTheLimits() {
        List<QuantRule> nineRules = java.util.stream.IntStream.rangeClosed(1, 9)
                .mapToObj(i -> HunterFixtures.grossMarginAtLeast("q" + i, "50")).toList();
        List<Criterion> sixChecks = java.util.stream.IntStream.rangeClosed(1, 6)
                .mapToObj(i -> HunterFixtures.criterion("s" + i, "Statement " + i)).toList();
        HunterSpec base = HunterFixtures.spec(nineRules, null, sixChecks);
        HunterSpec spec = new HunterSpec(2, null, "x".repeat(81), base.prompt(), "NASDAQ", base.quantRules(), null,
                base.criteria(), List.of(), 16);

        List<String> fields = HunterSpecValidator.validate(spec).stream().map(error -> error.field).toList();

        assertTrue(fields.containsAll(List.of("specVersion", "universe", "name", "candidateLimit", "quantRules",
                "criteria")), fields.toString());
    }

    @Test
    void needsAnEnabledRuleOrCheckToRun() {
        HunterSpec nothingOn = HunterFixtures.spec(
                List.of(new QuantRule("q1", false, null, Metric.PE_RATIO, NumericOp.LT, new BigDecimal("20"), null)),
                null, List.of());

        assertDoesNotThrow(() -> HunterSpecValidator.requireValid(nothingOn));
        HunterSpecInvalidException e = assertThrows(HunterSpecInvalidException.class,
                () -> HunterSpecValidator.requireRunnable(nothingOn));
        assertEquals("rules", e.getFieldErrors().get(0).field);
    }

    @Test
    void rejectsAnEmptyOrLongPrompt() {
        assertThrows(HunterSpecInvalidException.class, () -> HunterSpecValidator.requireValidPrompt("  "));
        assertThrows(HunterSpecInvalidException.class, () -> HunterSpecValidator.requireValidPrompt("x".repeat(501)));
        assertDoesNotThrow(() -> HunterSpecValidator.requireValidPrompt("x".repeat(500)));
    }

    @Test
    void validationErrorsCarryTheRejectedValue() {
        HunterSpec spec = HunterFixtures.spec(List.of(HunterFixtures.peBetween("q1", "30", "10")), null, List.of());

        FieldError error = HunterSpecValidator.validate(spec).get(0);

        assertEquals("quantRules[0].value2", error.field);
        assertEquals(new BigDecimal("10"), error.rejectedValue);
    }
}
