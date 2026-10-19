package com.neueda.leap.dto.response;

import java.util.UUID;
import java.math.BigDecimal;

public record HoldingResponse(
    UUID accountId,
    UUID instrumentId,
    String ticker,
    BigDecimal quantity
) {}