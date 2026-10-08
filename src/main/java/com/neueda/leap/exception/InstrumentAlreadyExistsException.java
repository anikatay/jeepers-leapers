package com.neueda.leap.exception;

public class InstrumentAlreadyExistsException extends  RuntimeException{
    public InstrumentAlreadyExistsException(String message){
        super(message);
    }
}
