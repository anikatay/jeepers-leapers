package com.neueda.leap.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateBalanceRequest (
    @NotNull (message = "Amount is required")
    @Positive (message = "Amount must be positive")
    BigDecimal amount
){}
