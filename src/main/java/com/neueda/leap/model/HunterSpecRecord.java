package com.neueda.leap.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A row of hunter_specs; spec_json is the HunterSpec as JSON. */
public class HunterSpecRecord {

    private UUID specId;
    private UUID userId;
    private String name;
    private String prompt;
    private String specJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public HunterSpecRecord() {}

    public HunterSpecRecord(UUID specId, UUID userId, String name, String prompt, String specJson,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.specId = specId;
        this.userId = userId;
        this.name = name;
        this.prompt = prompt;
        this.specJson = specJson;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getSpecId() { return specId; }
    public void setSpecId(UUID specId) { this.specId = specId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getSpecJson() { return specJson; }
    public void setSpecJson(String specJson) { this.specJson = specJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
