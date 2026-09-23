package com.neueda.leap.controller;

import com.neueda.leap.service.HoldingService;
import com.neueda.leap.dto.HoldingResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class HoldingController {

    private final HoldingService holdingService;

    public HoldingController(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    @GetMapping("/holding/{userId}")
    public ResponseEntity<HoldingResponse> getPortfolio(@PathVariable UUID userId) {
        HoldingResponse response = holdingService.getPortfolio(userId);

        if (response.getHoldings().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response);
    }
}