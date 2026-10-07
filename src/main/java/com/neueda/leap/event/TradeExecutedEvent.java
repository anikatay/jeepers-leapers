package com.neueda.leap.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TradeExecutedEvent(
        UUID tradeId,
        UUID accountId,
        UUID instrumentId,
        String side,
        int quantity,
        BigDecimal executionPrice,
        BigDecimal tradeValue,
        OffsetDateTime executedAt
    ) {}
