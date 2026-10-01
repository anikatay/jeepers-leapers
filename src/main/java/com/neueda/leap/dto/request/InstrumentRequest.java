package com.neueda.leap.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InstrumentRequest(
    @NotBlank (message = "exchangeId is Required")
    String exchangeId,
    
    @NotBlank (message = "ticker is required")
    String ticker,

    @NotBlank (message = "name is required")
    String name,

    @NotNull (message = "current price is Required")
    @Positive (message = "price must be positive")
    BigDecimal currentPrice  
) {
} 
