package com.neueda.leap.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class TradeResponse {

    private UUID tradeId;
    private String ticker;
    private String instrumentName;
    private String side;
    private BigDecimal quantity;
    private BigDecimal executionPrice;
    private BigDecimal tradeValue;
    private LocalDateTime executedAt;

    public TradeResponse() {
    }

    public TradeResponse(UUID tradeId, String ticker, String instrumentName, String side,
                         BigDecimal quantity, BigDecimal executionPrice, BigDecimal tradeValue,
                         LocalDateTime executedAt) {
        this.tradeId = tradeId;
        this.ticker = ticker;
        this.instrumentName = instrumentName;
        this.side = side;
        this.quantity = quantity;
        this.executionPrice = executionPrice;
        this.tradeValue = tradeValue;
        this.executedAt = executedAt;
    }

    public UUID getTradeId() {
        return tradeId;
    }

    public void setTradeId(UUID tradeId) {
        this.tradeId = tradeId;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public void setInstrumentName(String instrumentName) {
        this.instrumentName = instrumentName;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getExecutionPrice() {
        return executionPrice;
    }

    public void setExecutionPrice(BigDecimal executionPrice) {
        this.executionPrice = executionPrice;
    }

    public BigDecimal getTradeValue() {
        return tradeValue;
    }

    public void setTradeValue(BigDecimal tradeValue) {
        this.tradeValue = tradeValue;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }
}
