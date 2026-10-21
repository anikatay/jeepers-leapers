package com.neueda.leap.dto.response.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.neueda.leap.model.analytics.DashboardFlow;

public record DashboardFlowResponse(
        LocalDate bucketDate,
        BigDecimal buyVolume,
        BigDecimal sellVolume,
        BigDecimal netVolume,
        Long trades) {

    public static DashboardFlowResponse from(DashboardFlow model) {
        return new DashboardFlowResponse(
                model.bucketDate(),
                model.buyVolume(),
                model.sellVolume(),
                model.netVolume(),
                model.trades());
    }
}
