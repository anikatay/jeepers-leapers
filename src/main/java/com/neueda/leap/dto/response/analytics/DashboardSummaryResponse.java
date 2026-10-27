package com.neueda.leap.dto.response.analytics;

import java.math.BigDecimal;

import com.neueda.leap.model.analytics.DashboardSummary;

public record DashboardSummaryResponse(
        BigDecimal totalVolume,
        Long totalTrades,
        Long activeAccounts,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal totalPnl) {

    public static DashboardSummaryResponse from(DashboardSummary model) {
        return new DashboardSummaryResponse(
                model.totalVolume(),
                model.totalTrades(),
                model.activeAccounts(),
                model.realizedPnl(),
                model.unrealizedPnl(),
                model.totalPnl());
    }
}
