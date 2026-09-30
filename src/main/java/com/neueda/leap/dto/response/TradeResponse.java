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
    private String side;
    private int quantity;
    private BigDecimal executionPrice;
    private BigDecimal tradeValue;
    private OffsetDateTime executedAt;

    public TradeResponse() {
    }

    public TradeResponse(UUID tradeId, UUID accountId, UUID instrumentId, String ticker, String name, String side,
                         int quantity, BigDecimal executionPrice, BigDecimal tradeValue,
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

    public String getSide() {
        return side;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getExecutionPrice() {
        return executionPrice;
    } 

    public BigDecimal getTradeValue() {
        return tradeValue;
    }

    public OffsetDateTime getExecutedAt() {
        return executedAt;
    }

}
