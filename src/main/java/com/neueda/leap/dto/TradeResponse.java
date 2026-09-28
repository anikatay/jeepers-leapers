package com.neueda.leap.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class TradeResponse {

    private UUID tradeId;
    private UUID accountId;
    private String instrumentName;
    private String ticker;
    private String side;
    private BigDecimal quantity;
    private BigDecimal executionPrice;
    private BigDecimal tradeValue;
    private OffsetDateTime executedAt;

    public TradeResponse() {
    }

    public TradeResponse(UUID tradeId, UUID accountId, String instrumentName, String ticker, String side,
                          BigDecimal quantity, BigDecimal executionPrice, BigDecimal tradeValue,
                          OffsetDateTime executedAt) {
        this.tradeId = tradeId;
        this.accountId = accountId;
        this.instrumentName = instrumentName;
        this.ticker = ticker;
        this.side = side;
        this.quantity = quantity;
        this.executionPrice = executionPrice;
        this.tradeValue = tradeValue;
        this.executedAt = executedAt;
    }

    public UUID getTradeId() { return tradeId; }
    public void setTradeId(UUID tradeId) { this.tradeId = tradeId; }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public String getInstrumentName() { return instrumentName; }
    public void setInstrumentName(String instrumentName) { this.instrumentName = instrumentName; }

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getExecutionPrice() { return executionPrice; }
    public void setExecutionPrice(BigDecimal executionPrice) { this.executionPrice = executionPrice; }

    public BigDecimal getTradeValue() { return tradeValue; }
    public void setTradeValue(BigDecimal tradeValue) { this.tradeValue = tradeValue; }

    public OffsetDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(OffsetDateTime executedAt) { this.executedAt = executedAt; }
}