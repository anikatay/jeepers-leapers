package com.neueda.leap.model.analytics;

import java.time.LocalDate;

public record DashboardFlowQuery(
        LocalDate from,
        LocalDate to,
        String bucket) {
}
