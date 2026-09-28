package com.neueda.leap.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;


public class Account {

    private UUID accountId;

    private UUID userId;

    private String currency = "USD";

    private BigDecimal balance = BigDecimal.ZERO;

    private String status = "ACTIVE";

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;


    public Account() {
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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}