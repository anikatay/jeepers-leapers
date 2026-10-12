package com.neueda.leap.advice;

import com.neueda.leap.dto.response.ErrorResponse;
import com.neueda.leap.exception.ApplicationException;
import com.neueda.leap.exception.MarketDataUnavailableException;
import com.neueda.leap.exception.ObjectAlreadyExistsException;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // Verifies ObjectAlreadyExistsException is mapped to HTTP 409 with duplicate code.
    @Test
    void testHandleAlreadyExists() {
        HttpServletRequest request = request("/api/instruments");

        ResponseEntity<ErrorResponse> response = handler.handleAlreadyExists(
                new ObjectAlreadyExistsException("Instrument with ticker 'AAPL' already exists"),
                request
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("DUPLICATE_RESOURCE", body(response).getCode());
        assertEquals("/api/instruments", body(response).getPath());
    }

    // Verifies ObjectNotFoundException is mapped to HTTP 404 with expected payload fields.
    @Test
    void testHandleNotFound() {
        HttpServletRequest request = request("/api/accounts/1");

        ResponseEntity<ErrorResponse> response = handler.handleNotFound(
                new ObjectNotFoundException("Account not found"),
                request
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("RESOURCE_NOT_FOUND", body(response).getCode());
        assertEquals("Account not found", body(response).getDetails());
        assertEquals("/api/accounts/1", body(response).getPath());
    }

    // Verifies ObjectInvalidException is mapped to HTTP 400 with expected payload fields.
    @Test
    void testHandleInvalid() {
        HttpServletRequest request = request("/api/trades");

        ResponseEntity<ErrorResponse> response = handler.handleInvalid(
                new ObjectInvalidException("Quantity must be greater than 0"),
                request
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_DATA", body(response).getCode());
        assertEquals("Quantity must be greater than 0", body(response).getDetails());
    }

    // Verifies MethodArgumentNotValidException is mapped to validation payload with field errors.
    @Test
    void testHandleValidation() {
        HttpServletRequest request = request("/api/holdings");
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        org.springframework.validation.FieldError quantityError =
                new org.springframework.validation.FieldError("holdingRequest", "quantity", 0, false, null, null, "Quantity must be positive");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(quantityError));

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", body(response).getCode());
        assertNotNull(body(response).getFieldErrors());
        assertEquals(1, body(response).getFieldErrors().size());
        assertEquals("quantity", body(response).getFieldErrors().get(0).field);
        assertEquals("/api/holdings", body(response).getPath());
    }

    // Verifies ApplicationException preserves code/message/details and returns HTTP 400.
    @Test
    void testHandleApplicationException() {
        HttpServletRequest request = request("/api/market/history");

        ResponseEntity<ErrorResponse> response = handler.handleApplicationException(
                new ApplicationException("APP_001", "Business rule failed", "details here"),
                request
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("APP_001", body(response).getCode());
        assertEquals("Business rule failed", body(response).getMessage());
        assertEquals("details here", body(response).getDetails());
    }

    // Verifies duplicate-key constraint messages are mapped to user-friendly duplicate details.
    @Test
    void testHandleDataIntegrityViolationDuplicateKey() {
        HttpServletRequest request = request("/api/instruments");
        String message = "duplicate key value violates unique constraint \"instruments_ticker_key\"";

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException(message),
                request
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("DUPLICATE_RESOURCE", body(response).getCode());
        assertTrue(body(response).getDetails().contains("Duplicate entry"));
        assertTrue(body(response).getDetails().contains("instruments_ticker_key"));
    }

    // Verifies foreign-key constraint messages are mapped to invalid-reference details.
    @Test
    void testHandleDataIntegrityViolationForeignKey() {
        HttpServletRequest request = request("/api/trades");
        String message = "insert or update on table violates foreign key constraint";

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException(message),
                request
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Invalid reference: related record does not exist or cannot be deleted", body(response).getDetails());
    }

    // Verifies unknown data-integrity messages fall back to generic constraint details.
    @Test
    void testHandleDataIntegrityViolationFallbackDetails() {
        HttpServletRequest request = request("/api/accounts");

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("some db error"),
                request
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Database constraint violation: some db error", body(response).getDetails());
    }

    // Verifies market data outage exceptions are mapped to HTTP 503 and sanitized details.
    @Test
    void testHandleMarketDataUnavailable() {
        HttpServletRequest request = request("/api/market/history");

        ResponseEntity<ErrorResponse> response = handler.handleMarketDataUnavailable(
                new MarketDataUnavailableException("upstream down", new RuntimeException("timeout")),
                request
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("MARKET_DATA_UNAVAILABLE", body(response).getCode());
        assertEquals("Please try again shortly", body(response).getDetails());
    }

    // Verifies unexpected exceptions are mapped to HTTP 500 generic error response.
    @Test
    void testHandleGenericException() {
        HttpServletRequest request = request("/api/unknown");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(
                new RuntimeException("boom"),
                request
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", body(response).getCode());
        assertNull(body(response).getDetails());
        assertEquals("/api/unknown", body(response).getPath());
    }

    private HttpServletRequest request(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }

    private ErrorResponse body(ResponseEntity<ErrorResponse> response) {
        return response.getBody();
    }
}
