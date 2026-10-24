package com.neueda.leap.dto.response;

import java.util.List;

public record IntradayHistoryResponse(
        String ticker,
        String period,
        List<IntradayPriceBar> bars
) {}
