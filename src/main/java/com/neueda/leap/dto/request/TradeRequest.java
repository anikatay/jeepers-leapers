package com.neueda.leap.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public class TradeRequest {

    @NotNull(message = "Account ID is required")
    private UUID accountId;

    @NotBlank(message = "Ticker is required")
    @Pattern(regexp = "^[A-Z0-9]+$", message = "Ticker must contain only uppercase letters and numbers")
    private String ticker;

    @NotBlank(message = "Side is required")
    @Pattern(regexp = "^(BUY|SELL)$", message = "Side must be BUY or SELL")
    private String side;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be greater than 0")
    private BigDecimal quantity;

    public TradeRequest() {
    }

    public TradeRequest(UUID accountId, String ticker, String side, BigDecimal quantity) {
        this.accountId = accountId;
        this.ticker = ticker;
        this.side = side;
        this.quantity = quantity;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
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
}
