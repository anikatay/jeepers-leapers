package com.neueda.leap.dto.response.analytics;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.neueda.leap.model.Instrument;

public record InstrumentResponse(
        UUID instrumentId,
        String ticker,
        String name,
        String exchangeId,
        BigDecimal currentPrice,
        OffsetDateTime updatedAt) {

    public static InstrumentResponse from(Instrument model) {
        return new InstrumentResponse(
                model.getInstrumentId(),
                model.getTicker(),
                model.getName(),
                model.getExchange(),
                model.getCurrentPrice(),
                model.getUpdatedAt());
    }
}