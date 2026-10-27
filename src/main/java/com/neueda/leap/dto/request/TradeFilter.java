package com.neueda.leap.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TradeFilter(
        String search,
        String side,
        String currency,
        LocalDate fromDate,
        LocalDate toDate,
        BigDecimal minQuantity,
        BigDecimal maxQuantity,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal minTotal,
        BigDecimal maxTotal,
        String sortOrder) {
}