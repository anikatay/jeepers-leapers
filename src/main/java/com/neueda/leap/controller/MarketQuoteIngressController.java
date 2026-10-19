package com.neueda.leap.controller;

import com.neueda.leap.dto.response.MarketQuote;
import com.neueda.leap.service.MarketDataService;
import com.neueda.leap.websocket.MarketQuoteWebSocketHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/market")
public class MarketQuoteIngressController {

    private final MarketQuoteWebSocketHandler webSocketHandler;
    private final MarketDataService marketDataService;

    public MarketQuoteIngressController(
            MarketQuoteWebSocketHandler webSocketHandler,
            MarketDataService marketDataService) {
        this.webSocketHandler = webSocketHandler;
        this.marketDataService = marketDataService;
    }

    @PostMapping("/quotes")
    public ResponseEntity<Void> receiveQuote(@RequestBody MarketQuote quote) {
        marketDataService.cacheLatestQuote(quote);
        webSocketHandler.broadcast(quote);
        return ResponseEntity.accepted().build();
    }
}
