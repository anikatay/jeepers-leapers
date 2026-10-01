package com.neueda.leap.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record HoldingRequest (

    @NotNull(message = "Account ID is required")
    UUID accountId,

    @NotNull(message = "Instrument ID is required")
    UUID instrumentId,

    @Positive(message = "Quantity must be positive")
    int quantity
){}