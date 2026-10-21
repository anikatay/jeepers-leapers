package com.neueda.leap.dto.response;

import java.time.Instant;
import java.util.List;

public record MarketHeatmapResponse(
        String market,
        String selectionBasis,
        String universeVersion,
        Instant generatedAt,
        Instant asOf,
        List<MarketHeatmapSector> sectors) {
}
