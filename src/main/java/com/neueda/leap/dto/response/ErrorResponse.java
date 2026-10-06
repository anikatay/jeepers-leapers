package com.neueda.leap.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private String code;
    private String message;
    private String details;
    private LocalDateTime timestamp;
    private String path;
    private List<FieldError> fieldErrors;
    private Map<String, Object> context;
    private int status;

    public ErrorResponse(String code, String message, int status){
        this.code = code;
        this.message = message;
        this.status = status;
        this.timestamp = LocalDateTime.now();
    }
    
    public ErrorResponse(String code, String message, String details, int status){
        this(code, message, status);
        this.details = details;
    }

    public String getCode(){return code;}
    public String getMessage(){return message;}
    public int getStatus(){return status;}
    public String getDetails(){return details;}
    public LocalDateTime getTimestamp(){return timestamp;}
    public String getPath(){return path;}
    public List<FieldError> getFieldErrors(){return fieldErrors;}
    public Map<String, Object> getContext(){return context;}

    public void setPath(String path){this.path = path;}
    public void setFieldErrors(List<FieldError> fieldErrors){this.fieldErrors = fieldErrors;}
    public void setContext(Map<String, Object> context){this.context = context;}

}
