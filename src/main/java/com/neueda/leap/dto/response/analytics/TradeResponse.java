package com.neueda.leap.dto.response.analytics;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.neueda.leap.model.analytics.Trade;

public record TradeResponse(
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

    public static TradeResponse from(Trade model) {
        return new TradeResponse(
                model.tradeId(),
                model.accountId(),
                model.instrumentId(),
                model.ticker(),
                model.name(),
                model.side(),
                model.quantity(),
                model.executionPrice(),
                model.tradeValue(),
                model.executedAt(),
                model.pnl());
    }
}