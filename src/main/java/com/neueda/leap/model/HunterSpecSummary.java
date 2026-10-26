package com.neueda.leap.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One saved hunter in the caller's list, with its latest run if it has one. */
public class HunterSpecSummary {

    private UUID specId;
    private String name;
    private OffsetDateTime updatedAt;
    private OffsetDateTime lastRunAt;
    private Integer lastMatchCount;

    public UUID getSpecId() { return specId; }
    public void setSpecId(UUID specId) { this.specId = specId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public OffsetDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(OffsetDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
    public Integer getLastMatchCount() { return lastMatchCount; }
    public void setLastMatchCount(Integer lastMatchCount) { this.lastMatchCount = lastMatchCount; }
}
