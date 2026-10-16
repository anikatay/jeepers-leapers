package com.neueda.leap.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class TradeResponse {

    private UUID tradeId;
    private UUID accountId;
    private UUID instrumentId;
    private String ticker;
    private String name;
    private String instrumentCurrency;
    private String side;
    private BigDecimal quantity;
    private BigDecimal executionPrice;
    private BigDecimal tradeValue;
    private BigDecimal settlementValue;
    private String settlementCurrency;
    private BigDecimal fxRate;
    private OffsetDateTime executedAt;

    public TradeResponse() {
    }

    public TradeResponse(UUID tradeId, UUID accountId, UUID instrumentId, String ticker, String name, String side,
                         BigDecimal quantity, BigDecimal executionPrice, BigDecimal tradeValue,
                         OffsetDateTime executedAt) {
        this.tradeId = tradeId;
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.ticker = ticker;
        this.name = name;
        this.side = side;
        this.quantity = quantity;
        this.executionPrice = executionPrice;
        this.tradeValue = tradeValue;
        this.executedAt = executedAt;
    }

    public UUID getTradeId() {
        return tradeId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getInstrumentId() {
        return instrumentId;
    }

    public String getTicker(){
        return ticker;
    }

    public String getInstrumentName(){
        return name;
    }

    public String getInstrumentCurrency() {
        return instrumentCurrency;
    }

    public String getSide() {
        return side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getExecutionPrice() {
        return executionPrice;
    } 

    public BigDecimal getTradeValue() {
        return tradeValue;
    }

    public BigDecimal getSettlementValue() {
        return settlementValue;
    }

    public String getSettlementCurrency() {
        return settlementCurrency;
    }

    public BigDecimal getFxRate() {
        return fxRate;
    }

    public OffsetDateTime getExecutedAt() {
        return executedAt;
    }

}
