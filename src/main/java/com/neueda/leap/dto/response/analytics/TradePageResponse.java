package com.neueda.leap.dto.response.analytics;

import java.util.List;

import com.neueda.leap.model.analytics.TradePage;

public record TradePageResponse(
        List<TradeResponse> trades,
        long total,
        int page,
        int size) {

    public static TradePageResponse from(TradePage model) {
        return new TradePageResponse(
                model.trades().stream().map(TradeResponse::from).toList(),
                model.total(),
                model.page(),
                model.size());
    }
}