package com.neueda.leap.dto.request.analytics;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

public record PriceHistoryRequest(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate to,
        String interval) {
}
