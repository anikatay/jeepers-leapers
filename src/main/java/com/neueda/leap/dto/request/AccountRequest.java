package com.neueda.leap.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AccountRequest (

    @NotNull (message = "User is Required")
    UUID userId,
    
    @NotNull (message = "Balance is Required")
    @Positive (message = "Balance must be positive")
    BigDecimal initialBalance,

    @NotBlank (message = "currency is Required")
    String currency

) {}
