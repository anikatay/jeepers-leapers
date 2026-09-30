package com.neueda.leap.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ExchangeRequest(
    @NotBlank(message = "Exchange ID is required")
    String exchangeId,

    @NotBlank(message = "Exchange name is required")
    String name,

    @NotBlank(message = "Region is required")
    String region,

    @NotBlank(message = "Timezone is required")
    String timezone,

    @NotBlank(message = "Currency is required")
    String currency
) {
}
