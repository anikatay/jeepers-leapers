package com.neueda.leap.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record FxRateResponse(
        String baseCurrency,
        String quoteCurrency,
        BigDecimal rate,
        Instant quoteTime) {
}
