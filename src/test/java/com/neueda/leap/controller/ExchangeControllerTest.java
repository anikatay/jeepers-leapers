package com.neueda.leap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.dto.request.ExchangeRequest;
import com.neueda.leap.dto.response.ExchangeResponse;
import com.neueda.leap.exception.ObjectAlreadyExistsException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.service.ExchangeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExchangeController.class)
@AutoConfigureMockMvc(addFilters = false)
class ExchangeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ExchangeService exchangeService;

    // Verifies listing exchanges returns 200 OK and the response payload.
    @Test
    void testGetAllExchangesReturnsOk() throws Exception {
        List<ExchangeResponse> exchanges = List.of(
                new ExchangeResponse("XNYS", "New York Stock Exchange", "US", "America/New_York", "USD"),
                new ExchangeResponse("XLON", "London Stock Exchange", "UK", "Europe/London", "GBP")
        );

        when(exchangeService.getAllExchanges()).thenReturn(exchanges);

        mockMvc.perform(get("/api/exchanges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].exchangeId").value("XNYS"))
                .andExpect(jsonPath("$[0].name").value("New York Stock Exchange"))
                .andExpect(jsonPath("$[1].exchangeId").value("XLON"));
    }

    // Verifies unhandled service errors are translated to 500 INTERNAL_SERVER_ERROR.
    @Test
    void testGetAllExchangesReturnsInternalServerErrorOnUnhandledException() throws Exception {
        when(exchangeService.getAllExchanges()).thenThrow(new RuntimeException("database unavailable"));

        mockMvc.perform(get("/api/exchanges"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    // Verifies getting an existing exchange by id returns 200 OK.
    @Test
    void testGetExchangeByIdReturnsOk() throws Exception {
        ExchangeResponse response = new ExchangeResponse("XNYS", "New York Stock Exchange", "US", "America/New_York", "USD");

        when(exchangeService.getExchangeById("XNYS")).thenReturn(response);

        mockMvc.perform(get("/api/exchanges/{exchangeId}", "XNYS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exchangeId").value("XNYS"))
                .andExpect(jsonPath("$.region").value("US"));
    }

    // Verifies requesting an unknown exchange id returns 404 NOT_FOUND.
    @Test
    void testGetExchangeByIdReturnsNotFound() throws Exception {
        when(exchangeService.getExchangeById("UNKNOWN"))
                .thenThrow(new ObjectNotFoundException("Exchange not found: UNKNOWN"));

        mockMvc.perform(get("/api/exchanges/{exchangeId}", "UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
    }

    // Verifies creating an exchange with valid input returns 201 CREATED.
    @Test
    void testCreateExchangeReturnsCreated() throws Exception {
        ExchangeRequest request = new ExchangeRequest("XNYS", "New York Stock Exchange", "US", "America/New_York", "USD");
        ExchangeResponse response = new ExchangeResponse("XNYS", "New York Stock Exchange", "US", "America/New_York", "USD");

        when(exchangeService.createExchange(any(ExchangeRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/exchanges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/exchanges/XNYS"))
                .andExpect(jsonPath("$.exchangeId").value("XNYS"));
    }

    // Verifies creating an exchange with invalid input returns 400 BAD_REQUEST.
    @Test
    void testCreateExchangeReturnsBadRequestForInvalidInput() throws Exception {
        ExchangeRequest request = new ExchangeRequest("", "", "US", "America/New_York", "USD");

        mockMvc.perform(post("/api/exchanges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    // Verifies creating a duplicate exchange returns 409 CONFLICT.
    @Test
    void testCreateExchangeReturnsConflictForDuplicate() throws Exception {
        ExchangeRequest request = new ExchangeRequest("XNYS", "New York Stock Exchange", "US", "America/New_York", "USD");

        when(exchangeService.createExchange(any(ExchangeRequest.class)))
                .thenThrow(new ObjectAlreadyExistsException("Exchange already exists: XNYS"));

        mockMvc.perform(post("/api/exchanges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.status").value(409));
    }

    // Verifies updating an existing exchange returns 200 OK with updated content.
    @Test
    void testUpdateExchangeReturnsOk() throws Exception {
        ExchangeRequest request = new ExchangeRequest("IGNORED", "NYSE Updated", "US", "America/New_York", "USD");
        ExchangeResponse response = new ExchangeResponse("XNYS", "NYSE Updated", "US", "America/New_York", "USD");

        when(exchangeService.updateExchange(any(String.class), any(ExchangeRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/exchanges/{exchangeId}", "XNYS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exchangeId").value("XNYS"))
                .andExpect(jsonPath("$.name").value("NYSE Updated"));
    }

    // Verifies updating with invalid input returns 400 BAD_REQUEST.
    @Test
    void testUpdateExchangeReturnsBadRequestForInvalidInput() throws Exception {
        ExchangeRequest request = new ExchangeRequest("XNYS", "", "", "America/New_York", "USD");

        mockMvc.perform(patch("/api/exchanges/{exchangeId}", "XNYS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // Verifies updating a nonexistent exchange returns 404 NOT_FOUND.
    @Test
    void testUpdateExchangeReturnsNotFound() throws Exception {
        ExchangeRequest request = new ExchangeRequest("XNYS", "NYSE", "US", "America/New_York", "USD");

        when(exchangeService.updateExchange(any(String.class), any(ExchangeRequest.class)))
                .thenThrow(new ObjectNotFoundException("Exchange not found: XNYS"));

        mockMvc.perform(patch("/api/exchanges/{exchangeId}", "XNYS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // Verifies deleting an existing exchange returns 204 NO_CONTENT.
    @Test
    void testDeleteExchangeReturnsNoContent() throws Exception {
        doNothing().when(exchangeService).deleteExchange("XNYS");

        mockMvc.perform(delete("/api/exchanges/{exchangeId}", "XNYS"))
                .andExpect(status().isNoContent());

        verify(exchangeService).deleteExchange("XNYS");
    }

    // Verifies deleting a nonexistent exchange returns 404 NOT_FOUND.
    @Test
    void testDeleteExchangeReturnsNotFound() throws Exception {
        doThrow(new ObjectNotFoundException("Exchange not found: XNYS"))
                .when(exchangeService).deleteExchange("XNYS");

        mockMvc.perform(delete("/api/exchanges/{exchangeId}", "XNYS"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
