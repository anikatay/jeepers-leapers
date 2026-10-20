package com.neueda.leap.analysis;

public class TooManyAnalysesException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyAnalysesException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
