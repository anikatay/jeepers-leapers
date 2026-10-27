package com.neueda.leap.model.analytics;

import java.time.LocalDate;

public record IntradayQuery(
        String ticker,
        LocalDate date) {
}