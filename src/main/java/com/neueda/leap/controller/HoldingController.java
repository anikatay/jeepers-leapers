package com.neueda.leap.controller;

import com.neueda.leap.dto.response.HoldingResponse;
import com.neueda.leap.dto.request.HoldingRequest;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.service.AuthorizationService;
import com.neueda.leap.service.HoldingService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/holdings")
public class HoldingController {

    private final HoldingService holdingService;
    private final AuthorizationService authorizationService;

    public HoldingController(HoldingService holdingService, AuthorizationService authorizationService) {
        this.holdingService = holdingService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<List<HoldingResponse>> getPortfolio(@PathVariable UUID accountId, @AuthenticationPrincipal Jwt jwt) {
        UUID userId = authorizationService.getAuthenticatedUserId(jwt);
        authorizationService.requireAccountOwner(accountId, userId);

        List<HoldingResponse> response = holdingService.getHoldingsByAccountId(accountId);

        if (response.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{accountId}/{instrumentId}")
    public ResponseEntity<HoldingResponse> getHolding(@PathVariable UUID accountId, @PathVariable UUID instrumentId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = authorizationService.getAuthenticatedUserId(jwt);
        authorizationService.requireAccountOwner(accountId, userId);

        HoldingResponse response = holdingService.getHoldingByAccountIdAndInstrumentId(accountId, instrumentId);

        if (response == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<HoldingResponse> createHolding(@Valid @RequestBody HoldingRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = authorizationService.getAuthenticatedUserId(jwt);
        authorizationService.requireAccountOwner(request.accountId(), userId);

        HoldingResponse response = holdingService.createHolding(request);

        if (response == null) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{accountId}/{instrumentId}")
    public ResponseEntity<HoldingResponse> updateHoldingQuantity(@PathVariable UUID accountId,
            @PathVariable UUID instrumentId, @Valid @RequestBody HoldingRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = authorizationService.getAuthenticatedUserId(jwt);
        authorizationService.requireAccountOwner(accountId, userId);
        if (!accountId.equals(request.accountId()) || !instrumentId.equals(request.instrumentId())) {
            throw new ObjectInvalidException("Path IDs must match request IDs");
        }

        HoldingResponse response = holdingService.updateHoldingQuantity(request);

        if (response == null) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(response);
    }
    
}