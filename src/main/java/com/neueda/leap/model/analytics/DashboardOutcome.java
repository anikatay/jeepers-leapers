package com.neueda.leap.model.analytics;

import java.math.BigDecimal;
import java.util.UUID;

public record DashboardOutcome(
        UUID accountId,
        Long trades,
        BigDecimal notional,
        BigDecimal pnl,
        String outcome) {
}
