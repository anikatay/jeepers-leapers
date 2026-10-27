package com.neueda.leap.ai;

/** The model could not be reached, refused, was misconfigured or returned output that cannot be used. */
public class ModelCallException extends RuntimeException {

    public ModelCallException(String message) {
        super(message);
    }

    public ModelCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
