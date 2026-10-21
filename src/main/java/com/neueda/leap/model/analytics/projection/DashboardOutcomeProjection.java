package com.neueda.leap.model.analytics.projection;

import java.math.BigDecimal;
import java.util.UUID;

public record DashboardOutcomeProjection(
        UUID accountId,
        Long trades,
        BigDecimal notional,
        BigDecimal pnl) {
}
