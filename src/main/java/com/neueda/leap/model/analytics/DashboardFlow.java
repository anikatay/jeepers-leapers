package com.neueda.leap.model.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardFlow(
        LocalDate bucketDate,
        BigDecimal buyVolume,
        BigDecimal sellVolume,
        BigDecimal netVolume,
        Long trades) {
}
