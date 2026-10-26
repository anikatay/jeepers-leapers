package com.neueda.leap.hunter.catalog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** The 11 GICS sectors, written exactly as in the S&P 500 list. */
public enum GicsSector {
    COMMUNICATION_SERVICES("Communication Services"),
    CONSUMER_DISCRETIONARY("Consumer Discretionary"),
    CONSUMER_STAPLES("Consumer Staples"),
    ENERGY("Energy"),
    FINANCIALS("Financials"),
    HEALTH_CARE("Health Care"),
    INDUSTRIALS("Industrials"),
    INFORMATION_TECHNOLOGY("Information Technology"),
    MATERIALS("Materials"),
    REAL_ESTATE("Real Estate"),
    UTILITIES("Utilities");

    private final String label;

    GicsSector(String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }

    /** Unknown names read as null, so the validator can report them as field errors. */
    @JsonCreator
    public static GicsSector fromLabel(String label) {
        return Arrays.stream(values()).filter(sector -> sector.label.equals(label)).findFirst().orElse(null);
    }
}
