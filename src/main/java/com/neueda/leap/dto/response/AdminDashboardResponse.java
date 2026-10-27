package com.neueda.leap.dto.response;

import java.util.List;

public record AdminDashboardResponse(
        List<AdminDashboardRow> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}
