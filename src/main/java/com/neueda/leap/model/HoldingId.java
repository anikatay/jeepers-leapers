package com.neueda.leap.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class HoldingId implements Serializable {
    private UUID account;
    private UUID instrument;

    public HoldingId() {}
    public HoldingId(UUID account, UUID instrument) {
        this.account = account;
        this.instrument = instrument;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HoldingId)) return false;
        HoldingId that = (HoldingId) o;
        return account.equals(that.account) && instrument.equals(that.instrument);
    }

    @Override
    public int hashCode() {
        return Objects.hash(account, instrument);
    }
}