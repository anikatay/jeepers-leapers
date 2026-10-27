package com.neueda.leap.model.analytics.projection;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InstrumentProjection(
        UUID instrumentId,
        String ticker,
        String name,
        String exchangeId,
        BigDecimal currentPrice,
        OffsetDateTime updatedAt) {
}
