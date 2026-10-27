package com.neueda.leap.hunter.spec;

import com.neueda.leap.dto.response.FieldError;
import com.neueda.leap.hunter.HunterLimits;
import com.neueda.leap.hunter.spec.HunterSpec.Criterion;
import com.neueda.leap.hunter.spec.HunterSpec.NumericOp;
import com.neueda.leap.hunter.spec.HunterSpec.QuantRule;
import com.neueda.leap.hunter.spec.HunterSpec.SectorRule;
import com.neueda.leap.hunter.spec.HunterSpec.UnsupportedClause;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The rules in section 6.2 of the spec. Every problem is reported, not just the first. */
public final class HunterSpecValidator {

    private HunterSpecValidator() {}

    public static void requireValid(HunterSpec spec) {
        List<FieldError> errors = validate(spec);
        if (!errors.isEmpty()) {
            throw new HunterSpecInvalidException(errors);
        }
    }

    /** A spec can be saved without rules, but running it needs at least one enabled rule or check. */
    public static void requireRunnable(HunterSpec spec) {
        requireValid(spec);
        if (spec.enabledQuantRules().findAny().isEmpty() && !spec.sectorRuleEnabled()
                && spec.enabledCriteria().findAny().isEmpty()) {
            throw HunterSpecInvalidException.of("rules", "Turn on at least one filter or check before running", null);
        }
    }

    public static void requireValidPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw HunterSpecInvalidException.of("prompt", "Describe your idea first", prompt);
        }
        if (prompt.length() > HunterLimits.MAX_PROMPT_LENGTH) {
            throw HunterSpecInvalidException.of("prompt",
                    "At most " + HunterLimits.MAX_PROMPT_LENGTH + " characters", prompt.length());
        }
    }

    public static List<FieldError> validate(HunterSpec spec) {
        List<FieldError> errors = new ArrayList<>();
        if (spec == null) {
            errors.add(new FieldError("spec", "is required", null));
            return errors;
        }
        if (spec.specVersion() != HunterSpec.VERSION) {
            errors.add(new FieldError("specVersion", "must be " + HunterSpec.VERSION, spec.specVersion()));
        }
        if (!HunterSpec.SP500.equals(spec.universe())) {
            errors.add(new FieldError("universe", "must be " + HunterSpec.SP500, spec.universe()));
        }
        if (spec.name() == null || spec.name().isBlank()) {
            errors.add(new FieldError("name", "is required", spec.name()));
        } else if (spec.name().length() > HunterLimits.MAX_NAME_LENGTH) {
            errors.add(new FieldError("name", "at most " + HunterLimits.MAX_NAME_LENGTH + " characters", spec.name()));
        }
        if (spec.prompt() != null && spec.prompt().length() > HunterLimits.MAX_PROMPT_LENGTH) {
            errors.add(new FieldError("prompt", "at most " + HunterLimits.MAX_PROMPT_LENGTH + " characters",
                    spec.prompt().length()));
        }
        if (spec.candidateLimit() < 1 || spec.candidateLimit() > HunterLimits.MAX_CANDIDATES) {
            errors.add(new FieldError("candidateLimit", "must be between 1 and " + HunterLimits.MAX_CANDIDATES,
                    spec.candidateLimit()));
        }

        Set<String> ids = new HashSet<>();
        if (spec.quantRules().size() > HunterLimits.MAX_QUANT_RULES) {
            errors.add(new FieldError("quantRules", "at most " + HunterLimits.MAX_QUANT_RULES + " filters",
                    spec.quantRules().size()));
        }
        for (int i = 0; i < spec.quantRules().size(); i++) {
            validateQuantRule(spec.quantRules().get(i), "quantRules[" + i + "]", ids, errors);
        }
        if (spec.sectorRule() != null) {
            validateSectorRule(spec.sectorRule(), ids, errors);
        }
        if (spec.criteria().size() > HunterLimits.MAX_CRITERIA) {
            errors.add(new FieldError("criteria", "at most " + HunterLimits.MAX_CRITERIA + " checks",
                    spec.criteria().size()));
        }
        for (int i = 0; i < spec.criteria().size(); i++) {
            validateCriterion(spec.criteria().get(i), "criteria[" + i + "]", ids, errors);
        }
        for (int i = 0; i < spec.unsupported().size(); i++) {
            validateClause(spec.unsupported().get(i), "unsupported[" + i + "]", errors);
        }
        return errors;
    }

    private static void validateQuantRule(QuantRule rule, String path, Set<String> ids, List<FieldError> errors) {
        if (rule == null) {
            errors.add(new FieldError(path, "is required", null));
            return;
        }
        validateId(rule.id(), path, ids, errors);
        validateSourceText(rule.sourceText(), path, errors);
        if (rule.metric() == null) {
            errors.add(new FieldError(path + ".metric", "unknown metric", null));
        }
        if (rule.op() == null) {
            errors.add(new FieldError(path + ".op", "must be gt, gte, lt, lte or between", null));
        }
        if (rule.value() == null) {
            errors.add(new FieldError(path + ".value", "is required", null));
        }
        if (rule.op() == NumericOp.BETWEEN) {
            if (rule.value2() == null) {
                errors.add(new FieldError(path + ".value2", "is required for between", null));
            } else if (rule.value() != null && rule.value2().compareTo(rule.value()) <= 0) {
                errors.add(new FieldError(path + ".value2", "must be greater than value", rule.value2()));
            }
        } else if (rule.value2() != null) {
            errors.add(new FieldError(path + ".value2", "is only used with between", rule.value2()));
        }
    }

    private static void validateSectorRule(SectorRule rule, Set<String> ids, List<FieldError> errors) {
        validateId(rule.id(), "sectorRule", ids, errors);
        validateSourceText(rule.sourceText(), "sectorRule", errors);
        if (rule.op() == null) {
            errors.add(new FieldError("sectorRule.op", "must be in or notIn", null));
        }
        if (rule.values().isEmpty()) {
            errors.add(new FieldError("sectorRule.values", "choose at least one sector", null));
        }
        for (int i = 0; i < rule.values().size(); i++) {
            if (rule.values().get(i) == null) {
                errors.add(new FieldError("sectorRule.values[" + i + "]", "unknown sector", null));
            }
        }
    }

    private static void validateCriterion(Criterion criterion, String path, Set<String> ids, List<FieldError> errors) {
        if (criterion == null) {
            errors.add(new FieldError(path, "is required", null));
            return;
        }
        validateId(criterion.id(), path, ids, errors);
        validateSourceText(criterion.sourceText(), path, errors);
        if (criterion.statement() == null || criterion.statement().isBlank()) {
            errors.add(new FieldError(path + ".statement", "is required", criterion.statement()));
        } else if (criterion.statement().length() > HunterLimits.MAX_STATEMENT_LENGTH) {
            errors.add(new FieldError(path + ".statement",
                    "at most " + HunterLimits.MAX_STATEMENT_LENGTH + " characters", criterion.statement().length()));
        }
    }

    private static void validateClause(UnsupportedClause clause, String path, List<FieldError> errors) {
        if (clause == null) {
            errors.add(new FieldError(path, "is required", null));
            return;
        }
        if (clause.reason() == null) {
            errors.add(new FieldError(path + ".reason", "unknown reason", null));
        }
        validateSourceText(clause.sourceText(), path, errors);
    }

    private static void validateId(String id, String path, Set<String> ids, List<FieldError> errors) {
        if (id == null || id.isBlank()) {
            errors.add(new FieldError(path + ".id", "is required", id));
        } else if (!ids.add(id)) {
            errors.add(new FieldError(path + ".id", "must be unique within the hunter", id));
        }
    }

    private static void validateSourceText(String sourceText, String path, List<FieldError> errors) {
        if (sourceText != null && sourceText.length() > HunterLimits.MAX_SOURCE_TEXT_LENGTH) {
            errors.add(new FieldError(path + ".sourceText",
                    "at most " + HunterLimits.MAX_SOURCE_TEXT_LENGTH + " characters", sourceText.length()));
        }
    }
}
