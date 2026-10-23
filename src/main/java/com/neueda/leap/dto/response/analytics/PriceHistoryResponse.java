package com.neueda.leap.dto.response.analytics;

import java.util.List;

import com.neueda.leap.model.analytics.PriceHistory;

public record PriceHistoryResponse(
        String ticker,
        String interval,
        List<PriceBarResponse> bars) {

    public static PriceHistoryResponse from(PriceHistory model) {
        return new PriceHistoryResponse(
                model.ticker(),
                model.interval(),
                model.bars().stream().map(PriceBarResponse::from).toList());
    }
}