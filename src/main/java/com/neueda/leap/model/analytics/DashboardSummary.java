package com.neueda.leap.model.analytics;

import java.math.BigDecimal;

public record DashboardSummary(
        BigDecimal totalVolume,
        Long totalTrades,
        Long activeAccounts,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal totalPnl) {
}
