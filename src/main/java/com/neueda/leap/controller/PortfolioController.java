package com.neueda.leap.controller;

import com.neueda.leap.service.HoldingService;
import com.neueda.leap.dto.PortfolioResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class PortfolioController {

    private final HoldingService holdingService;

    public PortfolioController(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    @GetMapping("/portfolio/{userId}")
    public ResponseEntity<PortfolioResponse> getPortfolio(@PathVariable UUID userId) {
        PortfolioResponse portfolio = holdingService.getPortfolio(userId);

        if (portfolio.getHoldings().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(portfolio);
    }
}