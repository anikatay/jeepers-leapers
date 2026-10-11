package com.neueda.leap.model;

import java.util.UUID;
import java.math.BigDecimal;


public class Holding {

    private UUID accountId;

    private UUID instrumentId;

    private BigDecimal quantity;

    public Holding(UUID accountId, UUID instrumentId, BigDecimal quantity) {
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.quantity = quantity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Holding)) return false;
        Holding that = (Holding) o;
        return accountId.equals(that.accountId) && instrumentId.equals(that.instrumentId);
    }

    public UUID getAccountId() { return accountId; }

    public UUID getInstrumentId() { return instrumentId; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
}