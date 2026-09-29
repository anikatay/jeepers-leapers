package com.neueda.leap.dto.request;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Valid 
public record HoldingRequest(
    @NotBlank(message = "Instrument ID must be provided")
    UUID instrumentId,

    @Positive(message = "Quantity must be positive")
    int quantity
) {}
