package com.neueda.leap.controller;

import com.neueda.leap.dto.request.ExchangeRequest;
import com.neueda.leap.dto.response.ExchangeResponse;
import com.neueda.leap.service.ExchangeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
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
    public ResponseEntity<ExchangeResponse> getExchangeById(@PathVariable String exchangeId) {
        ExchangeResponse response = exchangeService.getExchangeById(exchangeId);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ExchangeResponse> createExchange(@Valid @RequestBody ExchangeRequest request) {
        ExchangeResponse response = exchangeService.createExchange(request);
        URI location = URI.create("/api/exchanges/" + response.exchangeId());
        return ResponseEntity.created(location).body(response);
    }

    @PatchMapping("/{exchangeId}")
    public ResponseEntity<ExchangeResponse> updateExchange(
            @PathVariable String exchangeId,
            @Valid @RequestBody ExchangeRequest request) {
        ExchangeResponse response = exchangeService.updateExchange(exchangeId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{exchangeId}")
    public ResponseEntity<Void> deleteExchange(@PathVariable String exchangeId) {
        exchangeService.deleteExchange(exchangeId);
        return ResponseEntity.noContent().build();
    }
}
