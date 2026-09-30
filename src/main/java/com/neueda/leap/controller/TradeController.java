package com.neueda.leap.controller;

import com.neueda.leap.dto.request.TradeRequest;
import com.neueda.leap.dto.response.TradeResponse;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.Trade;
import com.neueda.leap.service.InstrumentService;
import com.neueda.leap.service.TradeService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController 
@RequestMapping("/api/trades")
public class TradeController {

    private final TradeService tradeService;
    private final InstrumentService instrumentService;

    public TradeController(TradeService tradeService, InstrumentService instrumentService) {
        this.tradeService = tradeService;
        this.instrumentService = instrumentService;
    }

    @PostMapping
    public ResponseEntity<Trade> createTrade(@Valid @RequestBody TradeRequest request) {
        
        // Get instrument to fetch current price
        Instrument instrument = instrumentService.getInstrumentById(request.instrumentId());
        
        // Map request to Trade model
        Trade trade = new Trade();
        trade.setTradeId(UUID.randomUUID());
        trade.setAccountId(request.accountId());
        trade.setInstrumentId(request.instrumentId());
        trade.setSide(request.side());
        trade.setQuantity(request.quantity().intValue());
        trade.setExecutionPrice(instrument.getCurrentPrice());  // Use instrument's current price
        
        // Service will calculate tradeValue and update account balance
        Trade created = tradeService.createTrade(trade);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<TradeResponse>> getTradesByAccount(
            @PathVariable UUID accountId) {
        
        List<TradeResponse> trades = tradeService.getAllTradesByAccountWithInstrument(accountId);
        
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

}