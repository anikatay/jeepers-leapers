package com.neueda.leap.hunter;

/** The user already has the most hunters or watchlist items allowed; reported as 409 LIMIT_REACHED. */
public class LimitReachedException extends RuntimeException {

    public LimitReachedException(String message) {
        super(message);
    }
}
