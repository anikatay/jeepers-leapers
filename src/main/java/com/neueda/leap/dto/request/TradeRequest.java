package com.neueda.leap.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record TradeRequest (

    @NotNull(message = "Account ID is required")
    UUID accountId,

    @NotBlank(message = "Ticker is required")
    @Pattern(regexp = "^[A-Z0-9]+$", message = "Ticker must contain only uppercase letters and numbers")
    String ticker,

    @NotBlank(message = "Side is required")
    @Pattern(regexp = "^(BUY|SELL)$", message = "Side must be BUY or SELL")
    String side,    

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be greater than 0")
    BigDecimal quantity

){}