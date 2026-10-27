package com.neueda.leap.hunter.catalog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** The numeric rules a Hunter Spec can use. Every value is the model's estimate, never live data. */
public enum Metric {
    MARKET_CAP_USD_B("marketCapUsdB", "Market cap", "$ bn", "Share price × shares outstanding"),
    REVENUE_GROWTH_PCT("revenueGrowthPct", "Revenue growth", "%", "Latest fiscal year against the year before"),
    GROSS_MARGIN_PCT("grossMarginPct", "Gross margin", "%", "Gross profit ÷ revenue"),
    OPERATING_MARGIN_PCT("operatingMarginPct", "Operating margin", "%", "Operating income ÷ revenue"),
    FCF_MARGIN_PCT("fcfMarginPct", "Free cash flow margin", "%",
            "(Operating cash flow − capital expenditure) ÷ revenue"),
    PE_RATIO("peRatio", "P/E", "×", "Share price ÷ earnings per share"),
    PRICE_TO_SALES("priceToSales", "Price/sales", "×", "Market cap ÷ revenue");

    private final String id;
    private final String label;
    private final String unit;
    private final String definition;

    Metric(String id, String label, String unit, String definition) {
        this.id = id;
        this.label = label;
        this.unit = unit;
        this.definition = definition;
    }

    @JsonValue
    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }

    public String definition() {
        return definition;
    }

    /** Unknown IDs read as null, so the validator can report them as field errors. */
    @JsonCreator
    public static Metric fromId(String id) {
        return Arrays.stream(values()).filter(metric -> metric.id.equals(id)).findFirst().orElse(null);
    }
}
