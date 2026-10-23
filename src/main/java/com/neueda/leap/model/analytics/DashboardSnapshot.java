package com.neueda.leap.model.analytics;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.neueda.leap.model.Instrument;

public record DashboardSnapshot(
        LocalDate asOf,
        List<Instrument> instruments,
        Map<String, List<PriceBar>> history,
        Map<String, List<PriceBar>> intraday,
        List<Trade> trades) {
}