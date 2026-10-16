package com.neueda.leap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.dto.request.HoldingRequest;
import com.neueda.leap.dto.response.HoldingResponse;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.service.HoldingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HoldingController.class)
@AutoConfigureMockMvc(addFilters = false)
class HoldingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HoldingService holdingService;

    // Verifies portfolio endpoint returns 200 with holdings.
    @Test
    void testGetPortfolioReturnsOkWhenHoldingsExist() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        List<HoldingResponse> holdings = List.of(new HoldingResponse(accountId, instrumentId, "AAPL", BigDecimal.valueOf(10)));

        when(holdingService.getHoldingsByAccountId(accountId)).thenReturn(holdings);

        mockMvc.perform(get("/api/holdings/{accountId}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value(accountId.toString()))
                .andExpect(jsonPath("$[0].instrumentId").value(instrumentId.toString()))
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].quantity").value(10));
    }

    // Verifies portfolio endpoint returns 204 when service returns empty list.
    @Test
    void testGetPortfolioReturnsNoContentWhenEmpty() throws Exception {
        UUID accountId = UUID.randomUUID();

        when(holdingService.getHoldingsByAccountId(accountId)).thenReturn(List.of());

        mockMvc.perform(get("/api/holdings/{accountId}", accountId))
                .andExpect(status().isNoContent());
    }

    // Verifies portfolio endpoint returns 500 when service returns null.
    @Test
    void testGetPortfolioReturnsInternalServerErrorWhenNull() throws Exception {
        UUID accountId = UUID.randomUUID();

        when(holdingService.getHoldingsByAccountId(accountId)).thenReturn(null);

        mockMvc.perform(get("/api/holdings/{accountId}", accountId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    // Verifies portfolio endpoint returns 404 for service not found exceptions.
    @Test
    void testGetPortfolioReturnsNotFoundFromServiceException() throws Exception {
        UUID accountId = UUID.randomUUID();

        when(holdingService.getHoldingsByAccountId(accountId))
                .thenThrow(new ObjectNotFoundException("No holdings found for account"));

        mockMvc.perform(get("/api/holdings/{accountId}", accountId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

        // Verifies portfolio endpoint returns 500 for invalid UUID path values.
    @Test
        void testGetPortfolioReturnsInternalServerErrorForInvalidUuid() throws Exception {
        mockMvc.perform(get("/api/holdings/{accountId}", "not-a-uuid"))
                                .andExpect(status().isInternalServerError())
                                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    // Verifies holding endpoint returns 200 with a holding.
    @Test
    void testGetHoldingReturnsOkWhenFound() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        HoldingResponse response = new HoldingResponse(accountId, instrumentId, "MSFT", BigDecimal.valueOf(25));

        when(holdingService.getHoldingByAccountIdAndInstrumentId(accountId, instrumentId)).thenReturn(response);

        mockMvc.perform(get("/api/holdings/{accountId}/{instrumentId}", accountId, instrumentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("MSFT"))
                .andExpect(jsonPath("$.quantity").value(25));
    }

    // Verifies holding endpoint returns 404 when service returns null.
    @Test
    void testGetHoldingReturnsNotFoundWhenNull() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        when(holdingService.getHoldingByAccountIdAndInstrumentId(accountId, instrumentId)).thenReturn(null);

        mockMvc.perform(get("/api/holdings/{accountId}/{instrumentId}", accountId, instrumentId))
                .andExpect(status().isNotFound());
    }

    // Verifies holding endpoint returns 404 for service not found exceptions.
    @Test
    void testGetHoldingReturnsNotFoundFromServiceException() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        when(holdingService.getHoldingByAccountIdAndInstrumentId(accountId, instrumentId))
                .thenThrow(new ObjectNotFoundException("Holding not found"));

        mockMvc.perform(get("/api/holdings/{accountId}/{instrumentId}", accountId, instrumentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // Verifies create endpoint returns 200 with created holding.
    @Test
    void testCreateHoldingReturnsOkWhenValid() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        HoldingRequest request = new HoldingRequest(accountId, instrumentId, BigDecimal.valueOf(15));
        HoldingResponse response = new HoldingResponse(accountId, instrumentId, "GOOG", BigDecimal.valueOf(15));

        when(holdingService.createHolding(any(HoldingRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/holdings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("GOOG"))
                .andExpect(jsonPath("$.quantity").value(15));
    }

    // Verifies create endpoint returns 400 when service returns null.
    @Test
    void testCreateHoldingReturnsBadRequestWhenNull() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        HoldingRequest request = new HoldingRequest(accountId, instrumentId, BigDecimal.valueOf(15));

        when(holdingService.createHolding(any(HoldingRequest.class))).thenReturn(null);

        mockMvc.perform(post("/api/holdings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // Verifies create endpoint returns 400 for invalid request body.
    @Test
    void testCreateHoldingReturnsBadRequestForInvalidInput() throws Exception {
        String invalidPayload = "{\"accountId\":null,\"instrumentId\":null,\"quantity\":0}";

        mockMvc.perform(post("/api/holdings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // Verifies update endpoint returns 200 with updated holding.
    @Test
    void testUpdateHoldingReturnsOkWhenValid() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        HoldingRequest request = new HoldingRequest(accountId, instrumentId, BigDecimal.valueOf(30));
        HoldingResponse response = new HoldingResponse(accountId, instrumentId, "TSLA", BigDecimal.valueOf(30));

        when(holdingService.updateHoldingQuantity(any(HoldingRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/holdings/{accountId}/{instrumentId}", accountId, instrumentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("TSLA"))
                .andExpect(jsonPath("$.quantity").value(30));
    }

    // Verifies update endpoint returns 400 when service returns null.
    @Test
    void testUpdateHoldingReturnsBadRequestWhenNull() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        HoldingRequest request = new HoldingRequest(accountId, instrumentId, BigDecimal.valueOf(30));

        when(holdingService.updateHoldingQuantity(any(HoldingRequest.class))).thenReturn(null);

        mockMvc.perform(patch("/api/holdings/{accountId}/{instrumentId}", accountId, instrumentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // Verifies update endpoint returns 400 for invalid request body.
    @Test
    void testUpdateHoldingReturnsBadRequestForInvalidInput() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        String invalidPayload = "{\"accountId\":null,\"instrumentId\":null,\"quantity\":0}";

        mockMvc.perform(patch("/api/holdings/{accountId}/{instrumentId}", accountId, instrumentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
