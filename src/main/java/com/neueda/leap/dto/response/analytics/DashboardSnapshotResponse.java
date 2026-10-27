package com.neueda.leap.dto.response.analytics;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.neueda.leap.model.analytics.DashboardSnapshot;

public record DashboardSnapshotResponse(
        LocalDate asOf,
        List<InstrumentResponse> instruments,
        Map<String, List<PriceBarResponse>> history,
        Map<String, List<PriceBarResponse>> intraday,
        List<TradeResponse> trades) {

    public static DashboardSnapshotResponse from(DashboardSnapshot model) {
        return new DashboardSnapshotResponse(
                model.asOf(),
                model.instruments().stream().map(InstrumentResponse::from).toList(),
                toBars(model.history()),
                toBars(model.intraday()),
                model.trades().stream().map(TradeResponse::from).toList());
    }

    private static Map<String, List<PriceBarResponse>> toBars(
            Map<String, List<com.neueda.leap.model.analytics.PriceBar>> barsByTicker) {
        return barsByTicker.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream().map(PriceBarResponse::from).toList(),
                        (left, right) -> left,
                        java.util.LinkedHashMap::new));
    }
}