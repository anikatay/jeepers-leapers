package com.neueda.leap.advice;
import com.neueda.leap.analysis.TooManyAnalysesException;
import com.neueda.leap.analysis.TradeAnalysisException;
import com.neueda.leap.dto.response.ErrorResponse;
import com.neueda.leap.dto.response.FieldError;
import com.neueda.leap.exception.ObjectAlreadyExistsException;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ApplicationException;
import com.neueda.leap.exception.MarketDataUnavailableException;
import com.neueda.leap.exception.MarketClosedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;


@ControllerAdvice 
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException e,
            HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
            "AUTHENTICATION_FAILED",
            "Authentication failed",
            e.getMessage(),
            401
        );
        response.setPath(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException e,
            HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
                "ACCESS_DENIED",
                "You are not authorized to access this resource",
                e.getMessage(),
                HttpStatus.FORBIDDEN.value()
        );
        response.setPath(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException e,
            HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
                "INVALID_DATA",
                "The request body is invalid",
                "Request body must contain valid JSON",
                HttpStatus.BAD_REQUEST.value()
        );
        response.setPath(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MarketClosedException.class)
    public ResponseEntity<ErrorResponse> handleMarketClosed(
            MarketClosedException e,
            HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
                "MARKET_CLOSED",
                "The market is closed",
                e.getMessage(),
                HttpStatus.CONFLICT.value()
        );
        response.setPath(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
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

    @ExceptionHandler(TradeAnalysisException.class)
    public ResponseEntity<ErrorResponse> handleTradeAnalysisFailure(
            TradeAnalysisException e,
            HttpServletRequest request) {
        // The cause can quote the upstream error body, so it is logged and not returned.
        log.warn("Trade analysis failed for {}: {}", request.getRequestURI(), e.getMessage());
        ErrorResponse response = new ErrorResponse(
            "ANALYSIS_UNAVAILABLE",
            "The trade could not be analysed right now. Please try again later.",
            502
        );
        response.setPath(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

    @ExceptionHandler(TooManyAnalysesException.class)
    public ResponseEntity<ErrorResponse> handleTooManyAnalyses(
            TooManyAnalysesException e,
            HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
            "TOO_MANY_ANALYSES",
            "Too many trade analyses. Please wait before trying again.",
            e.getMessage(),
            429
        );
        response.setPath(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
            .body(response);
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

    @ExceptionHandler(MarketDataUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleMarketDataUnavailable(
            MarketDataUnavailableException e,
            HttpServletRequest request) {
        log.warn("Market data unavailable: {}", e.getMessage(), e.getCause());

        ErrorResponse response = new ErrorResponse(
            "MARKET_DATA_UNAVAILABLE",
            "Market data is temporarily unavailable",
            "Please try again shortly",
            503
        );
        response.setPath(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
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
        log.error("Unhandled exception on {}: {}", request.getRequestURI(), e.getMessage(), e);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
