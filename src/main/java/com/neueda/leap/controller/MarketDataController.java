package com.neueda.leap.controller;

import com.neueda.leap.client.MarketDataClient;
import com.neueda.leap.dto.response.HistoryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
public class MarketDataController {

	private final MarketDataClient client;

	public MarketDataController(MarketDataClient client) {
		this.client = client;
	}

	@GetMapping("/{ticker}/history")
	public HistoryResponse history(@PathVariable String ticker,
									@RequestParam(defaultValue = "1y") String period) {
		return client.fetchHistory(ticker, period);
	}
}