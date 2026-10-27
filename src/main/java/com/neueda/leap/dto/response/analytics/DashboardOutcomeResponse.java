package com.neueda.leap.dto.response.analytics;

import java.math.BigDecimal;
import java.util.UUID;

import com.neueda.leap.model.analytics.DashboardOutcome;

public record DashboardOutcomeResponse(
        UUID accountId,
        Long trades,
        BigDecimal notional,
        BigDecimal pnl,
        String outcome) {

    public static DashboardOutcomeResponse from(DashboardOutcome model) {
        return new DashboardOutcomeResponse(
                model.accountId(),
                model.trades(),
                model.notional(),
                model.pnl(),
                model.outcome());
    }
}
