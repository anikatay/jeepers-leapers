package com.neueda.leap.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketHeatmapTicker(
        String ticker,
        int rank,
        BigDecimal price,
        Instant quoteTime,
        Instant receivedTime,
        String source,
        QuoteStatus status) {
}
