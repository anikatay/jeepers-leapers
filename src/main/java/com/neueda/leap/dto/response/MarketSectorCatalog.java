package com.neueda.leap.dto.response;

import java.time.Instant;
import java.util.List;

public record MarketSectorCatalog(
        String market,
        String selectionBasis,
        String universeVersion,
        Instant generatedAt,
        List<MarketSector> sectors) {
}
