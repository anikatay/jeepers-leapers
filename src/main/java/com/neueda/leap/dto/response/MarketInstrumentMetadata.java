package com.neueda.leap.dto.response;

import java.math.BigDecimal;

public record MarketInstrumentMetadata(
        String ticker,
        String name,
        String exchangeId,
        String exchangeName,
        String region,
        String timezone,
        String currency,
        BigDecimal currentPrice) {
}
