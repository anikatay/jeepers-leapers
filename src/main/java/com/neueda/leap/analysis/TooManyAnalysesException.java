package com.neueda.leap.analysis;

public class TooManyAnalysesException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyAnalysesException(long retryAfterSeconds) {
        super("Too many trade analyses; try again in " + retryAfterSeconds + " seconds");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
