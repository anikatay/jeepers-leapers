package com.neueda.leap.model.analytics;

import java.util.List;

public record TradePage(
        List<Trade> trades,
        long total,
        int page,
        int size) {
}