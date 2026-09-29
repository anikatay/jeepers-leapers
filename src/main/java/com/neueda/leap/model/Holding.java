package com.neueda.leap.model;

import java.util.UUID;

public class Holding {

    private UUID accountId;

    private UUID instrumentId;

    private int quantity;

    public Holding(UUID accountId, UUID instrumentId, int quantity) {
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

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}