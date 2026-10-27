package com.neueda.leap.hunter.spec;

import com.neueda.leap.dto.response.FieldError;

import java.util.List;

/** A spec or prompt that breaks the rules in section 6.2 of the spec; reported as 400 with field errors. */
public class HunterSpecInvalidException extends RuntimeException {

    private final List<FieldError> fieldErrors;

    public HunterSpecInvalidException(List<FieldError> fieldErrors) {
        super("Invalid hunter: " + fieldErrors.size() + " problem(s)");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public static HunterSpecInvalidException of(String field, String message, Object rejectedValue) {
        return new HunterSpecInvalidException(List.of(new FieldError(field, message, rejectedValue)));
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
