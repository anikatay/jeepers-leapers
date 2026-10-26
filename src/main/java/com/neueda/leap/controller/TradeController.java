package com.neueda.leap.controller;

import com.neueda.leap.analysis.AnalysisRateLimiter;
import com.neueda.leap.analysis.TradeAnalysisContext;
import com.neueda.leap.analysis.TradeAnalysisResult;
import com.neueda.leap.analysis.TradeAnalysisService;
import com.neueda.leap.dto.request.TradeRequest;
import com.neueda.leap.dto.response.TradeResponse;
import com.neueda.leap.model.Trade;
import com.neueda.leap.service.AuthorizationService;
import com.neueda.leap.service.TradeService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
    private final AuthorizationService authorizationService;
    private final TradeAnalysisService tradeAnalysisService;
    private final AnalysisRateLimiter analysisRateLimiter;
    
    public TradeController(TradeService tradeService, AuthorizationService authorizationService, TradeAnalysisService tradeAnalysisService,
            AnalysisRateLimiter analysisRateLimiter) {
        this.tradeService = tradeService;
        this.authorizationService = authorizationService;
        this.tradeAnalysisService = tradeAnalysisService;
        this.analysisRateLimiter = analysisRateLimiter;
    }
    
    private UUID userIdOf(Jwt jwt) {
        return authorizationService.getAuthenticatedUserId(jwt);
    }


    

    @PostMapping
        public ResponseEntity<Trade> createTrade(
            @Valid @RequestBody TradeRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        
            // Map request to Trade model
        Trade trade = new Trade();
        trade.setTradeId(UUID.randomUUID());
        trade.setAccountId(request.accountId());
        trade.setInstrumentId(request.instrumentId());
        trade.setSide(request.side());
        trade.setQuantity(request.quantity());
        
        // Service will calculate tradeValue and update account balance
        UUID authenticatedUserId = authorizationService.getAuthenticatedUserId(jwt);
        Trade created = tradeService.createTrade(trade, authenticatedUserId);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<TradeResponse>> getTradesByAccount(
            @PathVariable UUID accountId,
            @RequestParam(required = false) Integer limit,
            @RequestParam (required = false) Integer offset,
            @AuthenticationPrincipal Jwt jwt) {
        authorizationService.requireAccountOwner(accountId, authorizationService.getAuthenticatedUserId(jwt));
        
        List<TradeResponse> trades = tradeService.getAllTradesByAccountWithInstrument(accountId, limit, offset);
        
        if (trades.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        
        return ResponseEntity.ok(trades);
    }

    @GetMapping("/account/{accountId}/instrument/{instrumentId}")
    public ResponseEntity<List<TradeResponse>> getTradesByAccountAndInstrument(
            @PathVariable UUID accountId,
            @PathVariable UUID instrumentId,
            @AuthenticationPrincipal Jwt jwt) {
        authorizationService.requireAccountOwner(accountId, authorizationService.getAuthenticatedUserId(jwt));
        
        List<TradeResponse> trades = tradeService.getTradesByAccountAndInstrument(accountId, instrumentId);
        
        if (trades.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        
        return ResponseEntity.ok(trades);
    }

    @PostMapping("/{tradeId}/analysis")
    public ResponseEntity<TradeAnalysisResult> analyzeTrade(@PathVariable UUID tradeId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = userIdOf(jwt);
        analysisRateLimiter.acquire(userId.toString());
        return ResponseEntity.ok(tradeAnalysisService.analyze(tradeId, userId));
    }

    /** What the model would be told about the trade, without calling it. */
    @GetMapping("/{tradeId}/analysis/context")
    public ResponseEntity<TradeAnalysisContext> getTradeAnalysisContext(@PathVariable UUID tradeId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(tradeAnalysisService.buildContext(tradeId, userIdOf(jwt)));
    }

}