package com.neueda.leap.exception;

public class ObjectInvalidException extends  RuntimeException{
    public ObjectInvalidException(String message){
        super(message);
    }

    public ObjectInvalidException(String message, Throwable cause){
        super(message, cause);
    }

}
