package com.neueda.leap.dto;

import java.math.BigDecimal;

/**
 * Flat DTO for the admin trades table.
 * Maps to columns: Client_ID (email), Instrument traded (name), Value (trade_value).
 */
public class TradeResponse {

    private String clientEmail;
    private String instrumentName;
    private BigDecimal tradeValue;

    public TradeResponse() {
    }

    public TradeResponse(String clientEmail, String instrumentName, BigDecimal tradeValue) {
        this.clientEmail = clientEmail;
        this.instrumentName = instrumentName;
        this.tradeValue = tradeValue;
    }

    public String getClientEmail() {
        return clientEmail;
    }

    public void setClientEmail(String clientEmail) {
        this.clientEmail = clientEmail;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public void setInstrumentName(String instrumentName) {
        this.instrumentName = instrumentName;
    }

    public BigDecimal getTradeValue() {
        return tradeValue;
    }

    public void setTradeValue(BigDecimal tradeValue) {
        this.tradeValue = tradeValue;
    }
}
