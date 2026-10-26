package com.neueda.leap.controller;

import com.neueda.leap.service.MarketDataService;
import com.neueda.leap.dto.response.HistoryResponse;
import com.neueda.leap.dto.response.IntradayHistoryResponse;
import com.neueda.leap.dto.response.FxRateResponse;
import com.neueda.leap.dto.response.MarketHeatmapResponse;
import com.neueda.leap.dto.response.MarketQuote;
import com.neueda.leap.dto.response.MarketSector;
import com.neueda.leap.dto.response.MarketSectorCatalog;
import com.neueda.leap.dto.response.NewsResponse;
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

	@GetMapping("/history/batch")
	public Map<String, HistoryResponse> historyBatch(
			@RequestParam String tickers,
			@RequestParam(defaultValue = "1y") String period) {
		return marketDataService.getHistoryBatch(tickers, period);
	}

	@GetMapping("/news")
	public NewsResponse news(
			@RequestParam String tickers,
			@RequestParam(defaultValue = "8") int limit) {
		return marketDataService.getNews(tickers, limit);
	}

	@GetMapping("/history/intraday/batch")
	public Map<String, IntradayHistoryResponse> intradayHistoryBatch(@RequestParam String tickers) {
		return marketDataService.getIntradayHistoryBatch(tickers);
	}

	@GetMapping("/{ticker}/history")
	public HistoryResponse history(@PathVariable String ticker,
									@RequestParam(defaultValue = "1y") String period) {
		return marketDataService.getHistory(ticker, period);
	}


	@GetMapping("/{ticker}/intraday-history")
	public IntradayHistoryResponse intradayHistory(@PathVariable String ticker) {
		return marketDataService.getIntradayHistory(ticker);
	}

	@GetMapping("/{ticker}/quote")
	public MarketQuote quote(@PathVariable String ticker) {
		return marketDataService.getQuoteOrFetch(ticker);

	}

	@GetMapping("/sectors")
	public MarketSectorCatalog sectors() {
		return marketDataService.getSectorCatalog();
	}

	@GetMapping("/sectors/{sectorId}")
	public MarketSector sector(@PathVariable String sectorId) {
		return marketDataService.getSector(sectorId);
	}

	@GetMapping("/heatmap")
	public MarketHeatmapResponse heatmap(
			@RequestParam(required = false) String sector) {
		return marketDataService.getHeatmap(sector);
	}

	@GetMapping("/fx-rate")
	public FxRateResponse exchangeRate(
			@RequestParam String base,
			@RequestParam String quote) {
		return new FxRateResponse(
				base.toUpperCase(),
				quote.toUpperCase(),
				marketDataService.getExchangeRate(base, quote),
				java.time.Instant.now());
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