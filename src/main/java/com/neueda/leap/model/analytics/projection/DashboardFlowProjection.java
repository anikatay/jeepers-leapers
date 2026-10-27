package com.neueda.leap.model.analytics.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardFlowProjection(
        LocalDate bucketDate,
        BigDecimal buyVolume,
        BigDecimal sellVolume,
        BigDecimal netVolume,
        Long trades) {
}
