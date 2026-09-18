package com.neueda.leap.controller;

import com.neueda.leap.dto.TradeResponse;
import com.neueda.leap.service.TradeService;
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

    private final TradeService tradeService;

    public TradeController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    @GetMapping("/trades/{userId}")
    public ResponseEntity<List<TradeResponse>> getTradesForUser(@PathVariable UUID userId) {
        List<TradeResponse> response = tradeService.getTradesForUser(userId);

        if (response.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response);
    }
}