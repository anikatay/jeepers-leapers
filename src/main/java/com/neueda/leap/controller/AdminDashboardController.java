package com.neueda.leap.controller;

import com.neueda.leap.dto.request.FreezeAccountRequest;
import com.neueda.leap.dto.response.AccountResponse;
import com.neueda.leap.dto.response.AdminDashboardResponse;
import com.neueda.leap.dto.response.TradeResponse;
import com.neueda.leap.service.AccountService;
import com.neueda.leap.service.AdminDashboardService;
import com.neueda.leap.service.AuthorizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;

import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {
    private final AdminDashboardService adminDashboardService;
    private final AuthorizationService authorizationService;
    private final AccountService accountService;

    public AdminDashboardController(AdminDashboardService adminDashboardService,
            AuthorizationService authorizationService, AccountService accountService) {
        this.adminDashboardService = adminDashboardService;
        this.authorizationService = authorizationService;
        this.accountService = accountService;
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

    @PatchMapping("/accounts/{accountId}/freeze")
    public ResponseEntity<AccountResponse> freezeAccount(
            @PathVariable(name = "accountId") UUID accountId,
            @Valid @RequestBody FreezeAccountRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        authorizationService.requireAdmin(jwt);
        return ResponseEntity.ok(AccountResponse.mapToResponse(
                accountService.freezeAccount(accountId, request.reason())));
    }

    @PatchMapping("/accounts/{accountId}/unfreeze")
    public ResponseEntity<AccountResponse> unfreezeAccount(
            @PathVariable(name = "accountId") UUID accountId,
            @AuthenticationPrincipal Jwt jwt) {
        authorizationService.requireAdmin(jwt);
        return ResponseEntity.ok(AccountResponse.mapToResponse(
                accountService.unfreezeAccount(accountId)));
    }
}
