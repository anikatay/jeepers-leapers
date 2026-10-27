package com.neueda.leap.model.analytics;

import java.time.LocalDate;
import java.util.List;

public record DashboardSnapshotQuery(
        LocalDate from,
        LocalDate to,
        List<String> symbols) {
}