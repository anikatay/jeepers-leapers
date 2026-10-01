package com.neueda.leap.dto.response;

public class ErrorResponse {
    private String code;
    private String message;
    private int status;

    public ErrorResponse(String code, String message, int status){
        this.code = code;
        this.message = message;
        this.status = status;
    }
    
    public String getCode(){return code;}
    public String getMessage(){return message;}
    public int getStatus(){return status;}

}
