package com.neueda.leap.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class Instrument {

    private UUID instrumentId;

    private String ticker;

    private String name;

    private String exchangeId;

    private String currency = "USD";

    private BigDecimal priceScale = BigDecimal.ONE;

    private BigDecimal currentPrice;

    private OffsetDateTime updatedAt;

    public Instrument() {
    }

    public UUID getInstrumentId() { return instrumentId; }
    public void setInstrumentId(UUID instrumentId) { this.instrumentId = instrumentId; }

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getExchange() { return exchangeId; }
    public void setExchange(String exchangeId) { this.exchangeId = exchangeId; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getPriceScale() { return priceScale; }
    public void setPriceScale(BigDecimal priceScale) { this.priceScale = priceScale; }

    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}