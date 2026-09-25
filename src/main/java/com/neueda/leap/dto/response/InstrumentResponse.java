package com.neueda.leap.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public class InstrumentResponse {

    private UUID instrumentId;
    private String ticker;
    private String name;
    private BigDecimal currentPrice;
    private String exchangeId;

    public InstrumentResponse() {
    }

    public InstrumentResponse(UUID instrumentId, String ticker, String name, BigDecimal currentPrice, String exchangeId) {
        this.instrumentId = instrumentId;
        this.ticker = ticker;
        this.name = name;
        this.currentPrice = currentPrice;
        this.exchangeId = exchangeId;
    }

    public UUID getInstrumentId() { return instrumentId; }
    public void setInstrumentId(UUID instrumentId) { this.instrumentId = instrumentId; }

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }

    public String getExchangeId() { return exchangeId; }
    public void setExchangeId(String exchangeId) { this.exchangeId = exchangeId; }
}