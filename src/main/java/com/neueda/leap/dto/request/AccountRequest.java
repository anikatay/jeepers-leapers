package com.neueda.leap.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class AccountRequest {

    @NotNull (message = "User is Required")
    private UUID userId;
    
    @NotNull (message = "Balance is Required")
    @Positive (message = "Balance must be positive")
    private BigDecimal initialBalance;

    @NotBlank (message = "currency is Required")
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
