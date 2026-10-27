package com.neueda.leap.dto.response.analytics;

import java.util.List;

public record PriceHistory(
        String ticker,
        String interval,
        List<PriceBar> bars) {

    public static PriceHistory from(String ticker, String interval, List<PriceBar> bars) {
        return new PriceHistory(
                ticker,
                interval,
                List.copyOf(bars));
    }
}