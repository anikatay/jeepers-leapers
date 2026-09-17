package com.neueda.leap.controller;

import com.neueda.leap.dto.AccountResponse;
import com.neueda.leap.service.AccountService;
import com.neueda.leap.model.Account;

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
        List<Account> accounts = accountService.getAccountsForUser(userId);
        if (accounts.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<AccountResponse> response = accounts.stream()
                .map(a -> new AccountResponse(
                        a.getAccountId(),
                        a.getCurrency(),
                        a.getBalance(),
                        a.getStatus(),
                        a.getCreatedAt()))
                .toList();

        return ResponseEntity.ok(response);
    }
}