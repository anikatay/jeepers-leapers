package com.neueda.leap.controller;

import com.neueda.leap.dto.response.AdminDashboardResponse;
import com.neueda.leap.dto.response.TradeResponse;
import com.neueda.leap.service.AdminDashboardService;
import com.neueda.leap.service.AuthorizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {
    private final AdminDashboardService adminDashboardService;
    private final AuthorizationService authorizationService;

    public AdminDashboardController(AdminDashboardService adminDashboardService,
            AuthorizationService authorizationService) {
        this.adminDashboardService = adminDashboardService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponse> getDashboard(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @AuthenticationPrincipal Jwt jwt) {
        authorizationService.requireAdmin(jwt);
        return ResponseEntity.ok(adminDashboardService.getDashboard(search, page, size));
    }

    @GetMapping("/accounts/{accountId}/trades")
    public ResponseEntity<List<TradeResponse>> getAccountTrades(
            @PathVariable(name = "accountId") UUID accountId,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "offset", required = false) Integer offset,
            @AuthenticationPrincipal Jwt jwt) {
        authorizationService.requireAdmin(jwt);
        return ResponseEntity.ok(adminDashboardService.getTrades(accountId, limit, offset));
    }
}
