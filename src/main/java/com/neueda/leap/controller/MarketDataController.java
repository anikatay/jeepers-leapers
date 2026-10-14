package com.neueda.leap.controller;

import com.neueda.leap.service.MarketDataService;
import com.neueda.leap.dto.response.HistoryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/market")
public class MarketDataController {

	private final MarketDataService marketDataService;

	public MarketDataController(MarketDataService marketDataService) {
		this.marketDataService = marketDataService;
	}

	@GetMapping("/{ticker}/history")
	public HistoryResponse history(@PathVariable String ticker,
									@RequestParam(defaultValue = "1y") String period) {
		return marketDataService.getHistory(ticker, period);
	}

	@PostMapping("/subscriptions/{ticker}")
	public ResponseEntity<Map<String, Object>> subscribeTicker(@PathVariable String ticker) {
		long count = marketDataService.subscribeTicker(ticker);
		return ResponseEntity.ok(Map.of(
				"ticker", ticker.toUpperCase(),
				"subscriberCount", count
		));
	}

	@DeleteMapping("/subscriptions/{ticker}")
	public ResponseEntity<Map<String, Object>> unsubscribeTicker(@PathVariable String ticker) {
		long count = marketDataService.unsubscribeTicker(ticker);
		return ResponseEntity.ok(Map.of(
				"ticker", ticker.toUpperCase(),
				"subscriberCount", count
		));
	}

	@GetMapping("/subscriptions")
	public ResponseEntity<Map<String, Long>> getSubscriptions() {
		return ResponseEntity.ok(marketDataService.getSubscriptions());
	}
}