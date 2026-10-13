package com.neueda.leap.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketQuote(
        String type,
        String ticker,
        BigDecimal price,
        Instant quoteTime,
        Instant receivedTime,
        String source
) {
}
