package com.neueda.leap.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EtlRefreshResult(
        @JsonProperty("run_id") String runId,
        String status,
        String error) {
}