package com.neueda.leap.controller;

import com.neueda.leap.dto.response.HoldingResponse;
import com.neueda.leap.service.HoldingService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class HoldingController {

    private final HoldingService holdingService;

    public HoldingController(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    @GetMapping("/holdings/{accountId}")
    public ResponseEntity<List<HoldingResponse>> getPortfolio(@PathVariable UUID accountId) {
        List<HoldingResponse> response = holdingService.getHoldingsByAccountId(accountId);

        if (response == null || response.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response);
    }
}