package com.neueda.leap.model.analytics;

import java.time.LocalDate;

public record PriceHistoryQuery(
        String ticker,
        LocalDate from,
        LocalDate to,
        String interval) {
}