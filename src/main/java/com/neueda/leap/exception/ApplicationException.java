package com.neueda.leap.exception;

public class ApplicationException extends RuntimeException {
    private String code;
    private String details;
    private Object data;

    public ApplicationException(String code, String message, String details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public String getCode() { return code; }
    public String getDetails() { return details; }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}
