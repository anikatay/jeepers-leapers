package com.neueda.leap.model.analytics;

import java.util.List;

public record PriceHistory(
        String ticker,
        String interval,
        List<PriceBar> bars) {
}