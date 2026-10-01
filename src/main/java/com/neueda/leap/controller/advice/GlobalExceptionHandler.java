package com.neueda.leap.controller.advice;
import com.neueda.leap.dto.response.ErrorResponse;
import com.neueda.leap.exception.InstrumentAlreadyExistsException;
import com.neueda.leap.exception.InstrumentNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice 
public class GlobalExceptionHandler {
    @ExceptionHandler(InstrumentAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleInstrumentAlreadyExists(InstrumentAlreadyExistsException e){
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("DUPLICATE_INSTRUMENT", e.getMessage(), 409));
    }

    @ExceptionHandler(InstrumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInstrumentNotFound(InstrumentNotFoundException e){
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("INSTRUMENT_NOT_FOUND", e.getMessage(), 404));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_INPUT", e.getMessage(), 400));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred", 500));
    }
}
