package com.neueda.leap.model.analytics.projection;

import java.math.BigDecimal;

public record DashboardSummaryProjection(
        BigDecimal totalVolume,
        Long totalTrades,
        Long activeAccounts,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal totalPnl) {
}
