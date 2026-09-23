package com.neueda.leap.controller;

import com.neueda.leap.dto.AccountResponse;
import com.neueda.leap.service.AccountService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/accounts/{userId}")
    public ResponseEntity<List<AccountResponse>> getAccountsForUser(@PathVariable UUID userId) {
        List<AccountResponse> response = accountService.getAccountsForUser(userId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/accounts")
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        List<AccountResponse> response = accountService.getAllAccounts();
        
        return ResponseEntity.ok(response);
    }
}