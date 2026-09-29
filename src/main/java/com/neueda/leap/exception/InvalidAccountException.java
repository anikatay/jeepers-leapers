package com.neueda.leap.exception;

public class InvalidAccountException extends  RuntimeException{
    public InvalidAccountException(String message){
        super(message);
    }
}
