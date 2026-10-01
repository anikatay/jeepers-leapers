package com.neueda.leap.controller;

import com.neueda.leap.dto.request.AccountRequest;
import com.neueda.leap.dto.request.UpdateBalanceRequest;
import com.neueda.leap.dto.response.AccountResponse;
import com.neueda.leap.model.Account;
import com.neueda.leap.service.AccountService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping; 
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // create account
    @PostMapping 
    public ResponseEntity<AccountResponse> createAccount(@RequestBody AccountRequest request){
        Account account = new Account();
        account.setUserId(request.userId());
        account.setBalance(request.initialBalance() != null ? request.initialBalance() : BigDecimal.ZERO);
        account.setCurrency(request.currency() != null ? request.currency() : "USD");

        Account createdAccount = accountService.createAccount(account);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.mapToResponse(createdAccount));
    }
    
    // get specific account
    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID accountId){
        Account account = accountService.getAccount(accountId);
        return ResponseEntity.ok(AccountResponse.mapToResponse(account));
    }

    // close account
    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> closeAccount(@PathVariable UUID accountId){
        accountService.closeAccount(accountId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{accountId}/deposit")
    public ResponseEntity<Void> incrementBalance(@PathVariable UUID accountId, @RequestBody UpdateBalanceRequest request){
        accountService.incrementBalance(accountId, request.amount());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{accountId}/withdraw")
    public ResponseEntity<Void> decrementBalance(@PathVariable UUID accountId, @RequestBody UpdateBalanceRequest request){
        accountService.decrementBalance(accountId, request.amount());
        return ResponseEntity.noContent().build();
    }

}