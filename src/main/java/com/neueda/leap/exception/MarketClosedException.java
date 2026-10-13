package com.neueda.leap.exception;

public class MarketClosedException extends RuntimeException {
    public MarketClosedException(String message) {
        super(message);
    }
}