package com.neueda.leap.dto.response;

public  class FieldError {
    public String field;
    public String message;
    public Object rejectedValue;

    public FieldError(String field, String message, Object rejectedValue){
        this.field = field;
        this.message = message;
        this.rejectedValue = rejectedValue;
    }
}
