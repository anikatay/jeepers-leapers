package com.neueda.leap.dto.response.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.neueda.leap.model.analytics.PriceBar;

public record PriceBarResponse(
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume) {

    public static PriceBarResponse from(PriceBar model) {
        return new PriceBarResponse(
                model.date(),
                model.open(),
                model.high(),
                model.low(),
                model.close(),
                model.volume());
    }
}