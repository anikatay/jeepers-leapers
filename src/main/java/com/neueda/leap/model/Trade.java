package com.neueda.leap.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class Trade {

    private UUID tradeId;

    private UUID accountId;

    private UUID instrumentId;

    private String side; // BUY, SELL

    private BigDecimal quantity;

    private BigDecimal executionPrice;

    private BigDecimal tradeValue;

    private BigDecimal settlementValue;

    private String settlementCurrency;

    private BigDecimal fxRate;

    private OffsetDateTime executedAt;

    public Trade() {
    }

    public UUID getTradeId() { return tradeId; }
    public void setTradeId(UUID tradeId) { this.tradeId = tradeId; }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public UUID getInstrumentId() { return instrumentId; }
    public void setInstrumentId(UUID instrumentId) { this.instrumentId = instrumentId; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getExecutionPrice() { return executionPrice; }
    public void setExecutionPrice(BigDecimal executionPrice) { this.executionPrice = executionPrice; }

    public BigDecimal getTradeValue() { return tradeValue; }
    public void setTradeValue(BigDecimal tradeValue ){this.tradeValue = tradeValue;}

    public BigDecimal getSettlementValue() { return settlementValue; }
    public void setSettlementValue(BigDecimal settlementValue) { this.settlementValue = settlementValue; }

    public String getSettlementCurrency() { return settlementCurrency; }
    public void setSettlementCurrency(String settlementCurrency) { this.settlementCurrency = settlementCurrency; }

    public BigDecimal getFxRate() { return fxRate; }
    public void setFxRate(BigDecimal fxRate) { this.fxRate = fxRate; }
    
    public OffsetDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(OffsetDateTime executedAt) { this.executedAt = executedAt; }
}