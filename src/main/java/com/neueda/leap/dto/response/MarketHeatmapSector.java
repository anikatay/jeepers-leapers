package com.neueda.leap.dto.response;

import java.util.List;

public record MarketHeatmapSector(
        String id,
        String name,
        List<MarketHeatmapTicker> tickers) {
}
