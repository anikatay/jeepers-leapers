package com.neueda.leap.dto.response;

import java.util.List;

public record MarketSector(
        String id,
        String name,
        List<MarketSectorTicker> tickers) {
}
