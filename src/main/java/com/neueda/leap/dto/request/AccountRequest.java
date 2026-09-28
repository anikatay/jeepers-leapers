package com.neueda.leap.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public class AccountRequest {
    private UUID userId;
    private BigDecimal initialBalance;
    private String currency;

    public UUID getUserId(){
        return  userId;
    }

    public void setUserId(UUID userId){
        this.userId = userId;
    }

    public BigDecimal getInitialBalance(){
        return  initialBalance;
    }

    public void setInitialBalance(BigDecimal initialBalance){
        this.initialBalance = initialBalance;
    }

    public String getCurrency(){
        return  currency;
    }

    public void setCurrency(String currency){
        this.currency = currency;
    }

}
