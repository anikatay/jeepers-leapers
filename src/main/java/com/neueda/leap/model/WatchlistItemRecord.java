package com.neueda.leap.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A row of watchlist_items, with the name of the hunter that found it if that hunter still exists. */
public class WatchlistItemRecord {

    private String ticker;
    private OffsetDateTime addedAt;
    private UUID sourceSpecId;
    private String sourceSpecName;

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }
    public OffsetDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(OffsetDateTime addedAt) { this.addedAt = addedAt; }
    public UUID getSourceSpecId() { return sourceSpecId; }
    public void setSourceSpecId(UUID sourceSpecId) { this.sourceSpecId = sourceSpecId; }
    public String getSourceSpecName() { return sourceSpecName; }
    public void setSourceSpecName(String sourceSpecName) { this.sourceSpecName = sourceSpecName; }
}
