package com.neueda.leap.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record HoldingRequest (

    @NotNull(message = "Account ID is required")
    private UUID accountId;

    @NotNull(message = "Instrument ID is required")
    private UUID instrumentId;

    @Positive(message = "Quantity must be positive")
    private int quantity;
){}