package com.neueda.leap.controller;

import com.neueda.leap.dto.ExchangeResponse;
import com.neueda.leap.service.ExchangeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/exchanges")
public class ExchangeController {

    private final ExchangeService exchangeService;

    public ExchangeController(ExchangeService exchangeService) {
        this.exchangeService = exchangeService;
    }

    @GetMapping
    public ResponseEntity<List<ExchangeResponse>> getAllExchanges() {
        return ResponseEntity.ok(exchangeService.getAllExchanges());
    }

    @GetMapping("/{exchangeId}")
    public ResponseEntity<ExchangeResponse> getByExchangeId(@PathVariable String exchangeId) {
        return exchangeService.getByExchangeId(exchangeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
