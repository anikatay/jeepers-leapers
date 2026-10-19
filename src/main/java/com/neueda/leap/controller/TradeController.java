package com.neueda.leap.controller;

import com.neueda.leap.analysis.AnalysisRateLimiter;
import com.neueda.leap.analysis.TradeAnalysisContext;
import com.neueda.leap.analysis.TradeAnalysisResult;
import com.neueda.leap.analysis.TradeAnalysisService;
import com.neueda.leap.dto.request.TradeRequest;
import com.neueda.leap.dto.response.TradeResponse;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.Trade;
import com.neueda.leap.service.InstrumentService;
import com.neueda.leap.service.TradeService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController 
@RequestMapping("/api/trades")
public class TradeController {

    private final TradeService tradeService;
    private final TradeAnalysisService tradeAnalysisService;
    private final AnalysisRateLimiter analysisRateLimiter;

    public TradeController(TradeService tradeService, TradeAnalysisService tradeAnalysisService,
            AnalysisRateLimiter analysisRateLimiter) {
        this.tradeService = tradeService;
        this.tradeAnalysisService = tradeAnalysisService;
        this.analysisRateLimiter = analysisRateLimiter;
    }

    @PostMapping
    public ResponseEntity<Trade> createTrade(@Valid @RequestBody TradeRequest request) {
        
            // Map request to Trade model
        Trade trade = new Trade();
        trade.setTradeId(UUID.randomUUID());
        trade.setAccountId(request.accountId());
        trade.setInstrumentId(request.instrumentId());
        trade.setSide(request.side());
        trade.setQuantity(request.quantity());
        
        // Service will calculate tradeValue and update account balance
        Trade created = tradeService.createTrade(trade);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<TradeResponse>> getTradesByAccount(
            @PathVariable UUID accountId,
            @RequestParam(required = false) Integer limit,
            @RequestParam (required = false) Integer offset) {
        
        List<TradeResponse> trades = tradeService.getAllTradesByAccountWithInstrument(accountId, limit, offset);
        
        if (trades.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        
        return ResponseEntity.ok(trades);
    }

    @GetMapping("/account/{accountId}/instrument/{instrumentId}")
    public ResponseEntity<List<TradeResponse>> getTradesByAccountAndInstrument(
            @PathVariable UUID accountId,
            @PathVariable UUID instrumentId) {
        
        List<TradeResponse> trades = tradeService.getTradesByAccountAndInstrument(accountId, instrumentId);
        
        if (trades.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        
        return ResponseEntity.ok(trades);
    }

    @PostMapping("/{tradeId}/analysis")
    public ResponseEntity<TradeAnalysisResult> analyzeTrade(@PathVariable UUID tradeId, HttpServletRequest request) {
        analysisRateLimiter.acquire(clientOf(request));
        return ResponseEntity.ok(tradeAnalysisService.analyze(tradeId));
    }

    /** What the model would be told about the trade, without calling it. */
    @GetMapping("/{tradeId}/analysis/context")
    public ResponseEntity<TradeAnalysisContext> getTradeAnalysisContext(@PathVariable UUID tradeId) {
        return ResponseEntity.ok(tradeAnalysisService.buildContext(tradeId));
    }

    // nginx sets X-Real-IP; a caller reaching the app directly can fake it, which the global cap covers.
    private static String clientOf(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        return realIp == null || realIp.isBlank() ? request.getRemoteAddr() : realIp;
    }

}