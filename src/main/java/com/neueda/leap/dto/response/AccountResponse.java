package com.neueda.leap.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.neueda.leap.model.Account;


public class AccountResponse {

    private UUID accountId;
    private UUID userId;
    private String currency;
    private BigDecimal balance;
    private String status;
    private String freezeReason;
    private OffsetDateTime createdAt;

    public AccountResponse() {
    }

    public AccountResponse(UUID accountId, UUID userId, String currency, BigDecimal balance, String status,
                           OffsetDateTime createdAt) {
        this(accountId, userId, currency, balance, status, null, createdAt);
    }

    public AccountResponse(UUID accountId, UUID userId, String currency, BigDecimal balance, String status,
                           String freezeReason, OffsetDateTime createdAt) {
        this.accountId = accountId;
        this.userId = userId;
        this.currency = currency;
        this.balance = balance;
        this.status = status;
        this.freezeReason = freezeReason;
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

    public String getFreezeReason() {
        return freezeReason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
    
    public static AccountResponse mapToResponse(Account account){
        return new AccountResponse(
            account.getAccountId(),
            account.getUserId(),
            account.getCurrency(),
            account.getBalance(),
            account.getStatus(),
            account.getFreezeReason(),
            account.getCreatedAt()
        );
    }
}
