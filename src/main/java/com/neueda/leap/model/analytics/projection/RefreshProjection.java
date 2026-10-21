package com.neueda.leap.model.analytics.projection;

import java.time.OffsetDateTime;

public record RefreshProjection(
        String etlRunId,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime lastEventAt,
        Long eventCount,
        Long rowCount,
        String errorMessage) {
}
