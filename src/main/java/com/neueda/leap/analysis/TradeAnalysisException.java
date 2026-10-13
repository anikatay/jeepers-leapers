package com.neueda.leap.analysis;

public class TradeAnalysisException extends RuntimeException {

    public TradeAnalysisException(String message) {
        super(message);
    }

    public TradeAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
