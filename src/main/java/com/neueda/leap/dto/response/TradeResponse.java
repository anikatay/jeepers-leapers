package com.neueda.leap.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class TradeResponse {

    private UUID tradeId;
    private String ticker;
    private String instrumentName;
    private String side;
    private int quantity;
    private BigDecimal executionPrice;
    private BigDecimal tradeValue;
    private LocalDateTime executedAt;

    public TradeResponse() {
    }

    public TradeResponse(UUID tradeId, String ticker, String instrumentName, String side,
                         int quantity, BigDecimal executionPrice, BigDecimal tradeValue,
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


    public String getTicker() {
        return ticker;
    }


    public String getInstrumentName() {
        return instrumentName;
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

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

}
