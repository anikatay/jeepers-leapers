package com.neueda.leap.dto.response;

public record ExchangeResponse(
    String exchangeId,
    String name,
    String region,
    String timezone,
    String currency
) {
}
