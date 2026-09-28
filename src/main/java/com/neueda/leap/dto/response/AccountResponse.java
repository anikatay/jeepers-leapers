package com.neueda.leap.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.neueda.leap.model.Account;


public class AccountResponse {

    private UUID accountId;
    private UUID userId;
    private String currency;
    private BigDecimal balance;
    private String status;
    private LocalDateTime createdAt;

    public AccountResponse() {
    }

    public AccountResponse(UUID accountId, UUID userId, String currency, BigDecimal balance, String status,
                           LocalDateTime createdAt) {
        this.accountId = accountId;
        this.userId = userId;
        this.currency = currency;
        this.balance = balance;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public static AccountResponse mapToResponse(Account account){
        return new AccountResponse(
            account.getAccountId(),
            account.getUserId(),
            account.getCurrency(),
            account.getBalance(),
            account.getStatus(),
            account.getCreatedAt()
        );
    }
}
