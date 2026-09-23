package com.neueda.leap.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class AccountResponse {

    private UUID accountId;
    private UUID userId;
    private String currency;
    private BigDecimal balance;
    private String status;
    private OffsetDateTime createdAt;

    public AccountResponse() {
    }

    public AccountResponse(UUID accountId,UUID userId, String currency, BigDecimal balance, String status, OffsetDateTime createdAt) {
        this.accountId = accountId;
        this.userId = userId;
        this.currency = currency;
        this.balance = balance;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}