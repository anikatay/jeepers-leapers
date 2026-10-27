package com.neueda.leap.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A row of hunter_runs; result_json is the HunterRun as JSON. */
public class HunterRunRecord {

    private UUID runId;
    private UUID specId;
    private String specHash;
    private OffsetDateTime ranAt;
    private String resultJson;
    private String model;
    private int matchCount;
    private int promptTokens;
    private int completionTokens;
    private long latencyMs;

    public HunterRunRecord() {}

    public HunterRunRecord(UUID runId, UUID specId, String specHash, OffsetDateTime ranAt, String resultJson,
            String model, int matchCount, int promptTokens, int completionTokens, long latencyMs) {
        this.runId = runId;
        this.specId = specId;
        this.specHash = specHash;
        this.ranAt = ranAt;
        this.resultJson = resultJson;
        this.model = model;
        this.matchCount = matchCount;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.latencyMs = latencyMs;
    }

    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }
    public UUID getSpecId() { return specId; }
    public void setSpecId(UUID specId) { this.specId = specId; }
    public String getSpecHash() { return specHash; }
    public void setSpecHash(String specHash) { this.specHash = specHash; }
    public OffsetDateTime getRanAt() { return ranAt; }
    public void setRanAt(OffsetDateTime ranAt) { this.ranAt = ranAt; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getMatchCount() { return matchCount; }
    public void setMatchCount(int matchCount) { this.matchCount = matchCount; }
    public int getPromptTokens() { return promptTokens; }
    public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
}
