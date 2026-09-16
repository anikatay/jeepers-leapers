package com.neueda.leap.controller;

import com.neueda.leap.dto.TradeResponse;
import com.neueda.leap.model.Trade;
import com.neueda.leap.repository.TradeRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class TradeController {

    private final TradeRepository tradeRepository;

    public TradeController(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    @GetMapping("/trades/{userId}")
    public ResponseEntity<List<TradeResponse>> getTradesForUser(@PathVariable UUID userId) {
        List<Trade> trades = tradeRepository.findByAccountUserUserId(userId);

        if (trades.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<TradeResponse> response = trades.stream()
                .map(t -> new TradeResponse(
                        t.getTradeId(),
                        t.getInstrument().getName(),
                        t.getInstrument().getTicker(),
                        t.getSide(),
                        t.getQuantity(),
                        t.getExecutionPrice(),
                        t.getTradeValue(),
                        t.getExecutedAt()))
                .toList();

        return ResponseEntity.ok(response);
    }
}