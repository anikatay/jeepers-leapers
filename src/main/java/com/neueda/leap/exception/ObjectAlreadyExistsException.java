package com.neueda.leap.exception;

public class ObjectAlreadyExistsException extends  RuntimeException{
    public ObjectAlreadyExistsException(String message){
        super(message);
    }
}
