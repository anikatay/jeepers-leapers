package com.neueda.leap.model.analytics;

import java.time.LocalDate;
import java.util.UUID;

public record TradeSearchQuery(
        LocalDate from,
        LocalDate to,
        String ticker,
        UUID accountId,
        String side,
        Integer page,
        Integer size) {
}