package com.neueda.leap.dto.response;

import java.util.List;

public record HistoryResponse(
	String ticker,
	String period,
	List<PriceBar> bars
) {}
