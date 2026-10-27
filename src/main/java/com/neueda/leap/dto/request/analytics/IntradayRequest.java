package com.neueda.leap.dto.request.analytics;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

public record IntradayRequest(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate date) {
}
