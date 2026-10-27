package com.neueda.leap.model.analytics;

import java.time.OffsetDateTime;

public record Refresh(
        String etlRunId,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime lastEventAt,
        Long eventCount,
        Long rowCount,
        String errorMessage) {
}