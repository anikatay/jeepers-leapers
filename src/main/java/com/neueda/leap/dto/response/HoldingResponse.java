package com.neueda.leap.dto.response;

import java.util.UUID;

public record HoldingResponse(
    UUID accountId,
    UUID instrumentId,
    int quantity
) {}