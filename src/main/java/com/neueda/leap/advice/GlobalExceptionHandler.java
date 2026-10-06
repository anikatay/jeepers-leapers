package com.neueda.leap.advice;
import com.neueda.leap.dto.response.ErrorResponse;
import com.neueda.leap.dto.response.FieldError;
import com.neueda.leap.exception.ObjectAlreadyExistsException;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ApplicationException;

import jakarta.servlet.http.HttpServletRequest;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;


@ControllerAdvice 
public class GlobalExceptionHandler {
    @ExceptionHandler(ObjectAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyExists(ObjectAlreadyExistsException e, HttpServletRequest request){
        ErrorResponse response = new ErrorResponse(
            "DUPLICATE_RESOURCE",
            "Resource already exists",
            e.getMessage(),  // e.g., "Instrument with ticker 'AAPL' already exists"
            409
        );
        response.setPath(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(ObjectNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ObjectNotFoundException e,
            HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
            "RESOURCE_NOT_FOUND",
            "The requested resource does not exist",
            e.getMessage(),
            404
        );
        response.setPath(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
     @ExceptionHandler(ObjectInvalidException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(
            ObjectInvalidException e,
            HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
            "INVALID_DATA",
            "The provided data is invalid",
            e.getMessage(),
            400
        );
        response.setPath(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
     @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException e,
            HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
            "VALIDATION_ERROR",
            "Input validation failed",
            400
        );
        response.setPath(request.getRequestURI());
        
        // Extract field-specific errors
        var fieldErrors = e.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(fe -> new FieldError(
                fe.getField(),
                fe.getDefaultMessage(),
                fe.getRejectedValue()
            ))
            .collect(Collectors.toList());
        
        response.setFieldErrors(fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> handleApplicationException(
            ApplicationException e,
            HttpServletRequest request) { 
        ErrorResponse response = new ErrorResponse(
            e.getCode(),
            e.getMessage(),
            e.getDetails(),
            400
        );
        response.setPath(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException e,
            HttpServletRequest request) {
        
        String message = e.getMessage();
        String details = extractConstraintDetails(message);
        
        ErrorResponse response = new ErrorResponse(
            "DUPLICATE_RESOURCE",
            "The item already exists",
            details,
            409
        );
        response.setPath(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    private String extractConstraintDetails(String message) {
        // Parse PostgreSQL error message to extract constraint info
        if (message != null) {
            if (message.contains("duplicate key value violates unique constraint")) {
                int start = message.indexOf("\"");
                int end = message.indexOf("\"", start + 1);
                if (start != -1 && end != -1) {
                    String constraint = message.substring(start, end + 1);
                    return "Duplicate entry: " + constraint + " already exists";
                }
            }
            if (message.contains("violates foreign key constraint")) {
                return "Invalid reference: related record does not exist or cannot be deleted";
            }
        }
        return "Database constraint violation: " + message;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception e,
            HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
            "INTERNAL_ERROR",
            "An unexpected error occurred. Please contact support if the problem persists.",
            500
        );
        response.setPath(request.getRequestURI());
        
        // Log the full exception server-side (don't expose to client)
        e.printStackTrace();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
