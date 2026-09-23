package com.neueda.leap.controller;

import com.neueda.leap.service.HoldingService;
import com.neueda.leap.dto.HoldingResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class HoldingController {

    private final HoldingService holdingService;

    public HoldingController(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    @GetMapping("/holdings/{userId}")
    public ResponseEntity<HoldingResponse> getPortfolio(@PathVariable UUID userId) {
        HoldingResponse response = holdingService.getPortfolio(userId);

        if (response.getHoldings().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/holdings")
    public ResponseEntity<List<HoldingResponse>> getAllHoldings() {

        return ResponseEntity.notFound().build();
    }

    @GetMapping("/holdings/{accountId}")
    public ResponseEntity<HoldingResponse> getHoldingsByAccount(@PathVariable UUID accountId) {
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/holdings")
    public ResponseEntity<HoldingResponse> createHolding(@RequestBody HoldingResponse holdingResponse) {
        return ResponseEntity.notFound().build();
    }

    @PatchMapping("/{holdings/{accountId}/{instrumentId}/{quantity}")
    public void updateHolding(@PathVariable UUID accountId, @PathVariable UUID instrumentId, @PathVariable int quantity) {
        // TODO: Implement the logic to update the holding here
    }

}