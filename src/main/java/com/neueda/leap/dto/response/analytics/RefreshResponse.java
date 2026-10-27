package com.neueda.leap.dto.response.analytics;

import java.time.OffsetDateTime;

import com.neueda.leap.model.analytics.Refresh;

public record RefreshResponse(
        String etlRunId,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime lastEventAt,
        Long eventCount,
        Long rowCount,
        String errorMessage) {

    public static RefreshResponse from(Refresh model) {
        return new RefreshResponse(
                model.etlRunId(),
                model.status(),
                model.startedAt(),
                model.lastEventAt(),
                model.eventCount(),
                model.rowCount(),
                model.errorMessage());
    }
}