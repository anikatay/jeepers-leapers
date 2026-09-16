package com.neueda.leap.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "trade_id")
    private UUID tradeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Column(nullable = false)
    private String side; // BUY, SELL

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal quantity;

    @Column(name = "execution_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal executionPrice;

    @Column(name = "trade_value", insertable = false, updatable = false, precision = 18, scale = 4)
    private BigDecimal tradeValue;

    @Column(name = "executed_at", nullable = false)
    private OffsetDateTime executedAt;

    public Trade() {
    }

    public UUID getTradeId() { return tradeId; }
    public void setTradeId(UUID tradeId) { this.tradeId = tradeId; }

    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }

    public Instrument getInstrument() { return instrument; }
    public void setInstrument(Instrument instrument) { this.instrument = instrument; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getExecutionPrice() { return executionPrice; }
    public void setExecutionPrice(BigDecimal executionPrice) { this.executionPrice = executionPrice; }

    public BigDecimal getTradeValue() { return tradeValue; }

    public OffsetDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(OffsetDateTime executedAt) { this.executedAt = executedAt; }
}