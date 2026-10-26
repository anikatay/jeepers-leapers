package com.neueda.leap.dto.response;

import java.util.List;

public record PaginatedTradeResponse(
        List<TradeResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}