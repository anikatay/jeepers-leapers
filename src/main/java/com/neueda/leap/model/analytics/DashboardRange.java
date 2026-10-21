package com.neueda.leap.model.analytics;

import java.time.LocalDate;

public record DashboardRange(
        LocalDate from,
        LocalDate to) {
}
