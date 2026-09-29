package com.neueda.leap.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class UpdateBalanceRequest {
    @NotNull 
    @Positive 
    private BigDecimal amount;

    public BigDecimal getAmount(){
        return  amount;
    }
    public void setAmount(BigDecimal amount){
        this.amount = amount;
    }
}
