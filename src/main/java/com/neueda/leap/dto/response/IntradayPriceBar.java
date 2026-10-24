package com.neueda.leap.dto.response;

import java.math.BigDecimal;

public record IntradayPriceBar(
        String date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume
) {}
