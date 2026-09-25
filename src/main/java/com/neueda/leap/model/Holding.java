package com.neueda.leap.model;

public class Holding {


    private Account account;

    private Instrument instrument;

    private int quantity;

    public Holding() {
    }

    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }

    public Instrument getInstrument() { return instrument; }
    public void setInstrument(Instrument instrument) { this.instrument = instrument; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}