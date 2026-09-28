package com.neueda.leap.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;


public class Account {

    private UUID accountId;

    private UUID userId;

    private String currency = "USD";

    private BigDecimal balance = BigDecimal.ZERO;

    private String status = "ACTIVE";

    private OffsetDateTime createdAt;

    public Account() {
    }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public User getUser() { return userId; }
    public void setUser(UUID userId) { this.userId = userId; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}