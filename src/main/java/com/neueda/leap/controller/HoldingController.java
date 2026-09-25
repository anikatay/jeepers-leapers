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
        // call service to get all holdings for admin only. must check if admin
        List<HoldingResponse> response = holdingService.getAllHoldings();
        if (response.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/holdings/{accountId}")
    public ResponseEntity<HoldingResponse> getHoldingsByAccount(@PathVariable UUID accountId) {
        // TODO: check if account matches the logged-in user or if the user is an admin

        // call service to get holdings by account if authorized
        HoldingResponse response = holdingService.getHoldingsByAccount(accountId);

        if (response.getHoldings().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/holdings")
    public ResponseEntity<HoldingResponse> createHolding(@RequestBody HoldingResponse holdingResponse) {
        // validate holding
        HoldingResponse validatedResponse = holdingResponse;
        if (validatedResponse.getHoldings().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HoldingResponse response =holdingService.createHolding(validatedResponse);
    
        return ResponseEntity.ok(validatedResponse);
    }

    @PatchMapping("/{holdings/{accountId}/{instrumentId}/{quantity}")
    public void updateHolding(@PathVariable UUID accountId, @PathVariable UUID instrumentId, @PathVariable int quantity) {
        // TODO: Implement the logic to update the holding here
    }

}