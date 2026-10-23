package com.neueda.leap.model.analytics;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Trade(
        UUID tradeId,
        UUID accountId,
        UUID instrumentId,
        String ticker,
        String name,
        String side,
        BigDecimal quantity,
        BigDecimal executionPrice,
        BigDecimal tradeValue,
        OffsetDateTime executedAt,
        BigDecimal pnl) {
}