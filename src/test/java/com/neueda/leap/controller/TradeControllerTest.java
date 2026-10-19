package com.neueda.leap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.analysis.AnalysisRateLimiter;
import com.neueda.leap.analysis.TradeAnalysisService;
import com.neueda.leap.dto.request.TradeRequest;
import com.neueda.leap.dto.response.TradeResponse;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.Trade;
import com.neueda.leap.service.InstrumentService;
import com.neueda.leap.service.TradeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TradeController.class)
@AutoConfigureMockMvc(addFilters = false)
class TradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TradeService tradeService;

    @MockBean
    private InstrumentService instrumentService;

    // Covered by TradeAnalysisEndpointTest; mocked here so the controller can be created
    @MockBean
    private TradeAnalysisService tradeAnalysisService;

    @MockBean
    private AnalysisRateLimiter analysisRateLimiter;

    // Verifies creating a trade returns 201 with created trade fields.
    @Test
    void testCreateTradeReturnsCreatedForValidRequest() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        UUID tradeId = UUID.randomUUID();

        TradeRequest request = new TradeRequest(accountId, instrumentId, "BUY", BigDecimal.valueOf(10));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);
        instrument.setCurrentPrice(BigDecimal.valueOf(100.50));

        Trade created = new Trade();
        created.setTradeId(tradeId);
        created.setAccountId(accountId);
        created.setInstrumentId(instrumentId);
        created.setSide("BUY");
        created.setQuantity(BigDecimal.valueOf(10));
        created.setExecutionPrice(BigDecimal.valueOf(100.50));
        created.setTradeValue(BigDecimal.valueOf(1005.00));
        created.setExecutedAt(OffsetDateTime.now());

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        when(tradeService.createTrade(any(Trade.class))).thenReturn(created);

        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tradeId").value(tradeId.toString()))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.instrumentId").value(instrumentId.toString()))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.quantity").value(10));
    }

    // Verifies creating a trade rejects invalid request payload.
    @Test
    void testCreateTradeReturnsBadRequestForInvalidInput() throws Exception {
        String invalidPayload = "{\"accountId\":null,\"instrumentId\":null,\"side\":\"HOLD\",\"quantity\":0}";

        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // Verifies creating a trade returns 404 when instrument is not found.
    @Test
    void testCreateTradeReturnsNotFoundWhenInstrumentMissing() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
                TradeRequest request = new TradeRequest(accountId, instrumentId, "BUY", BigDecimal.valueOf(10));

        when(tradeService.createTrade(any(Trade.class)))
                .thenThrow(new ObjectNotFoundException("Instrument not found: " + instrumentId));

        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // Verifies creating a trade returns 400 for business validation failures.
    @Test
    void testCreateTradeReturnsBadRequestForBusinessValidationFailure() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
                TradeRequest request = new TradeRequest(accountId, instrumentId, "BUY", BigDecimal.valueOf(10));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);
        instrument.setCurrentPrice(BigDecimal.valueOf(100.50));

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        when(tradeService.createTrade(any(Trade.class)))
                .thenThrow(new ObjectInvalidException("Insufficient balance for trade"));

        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATA"));
    }

    // Verifies creating a trade returns 500 for unhandled exceptions.
    @Test
    void testCreateTradeReturnsInternalServerErrorForUnhandledException() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
                TradeRequest request = new TradeRequest(accountId, instrumentId, "BUY", BigDecimal.valueOf(10));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);
        instrument.setCurrentPrice(BigDecimal.valueOf(100.50));

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        when(tradeService.createTrade(any(Trade.class))).thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    // Verifies getting trades by account returns 200 with results.
    @Test
    void testGetTradesByAccountReturnsOkWhenTradesExist() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        TradeResponse response = new TradeResponse(
                UUID.randomUUID(),
                accountId,
                instrumentId,
                "AAPL",
                "Apple Inc.",
                "BUY",
                BigDecimal.valueOf(5),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(500),
                OffsetDateTime.now()
        );

        when(tradeService.getAllTradesByAccountWithInstrument(accountId, null, null)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/trades/account/{accountId}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value(accountId.toString()))
                .andExpect(jsonPath("$[0].ticker").value("AAPL"));
    }

    // Verifies getting trades by account returns 204 when no trades exist.
    @Test
    void testGetTradesByAccountReturnsNoContentWhenEmpty() throws Exception {
        UUID accountId = UUID.randomUUID();

        when(tradeService.getAllTradesByAccountWithInstrument(accountId, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/trades/account/{accountId}", accountId))
                .andExpect(status().isNoContent());
    }

    // Verifies getting trades by account returns 404 for service not found errors.
    @Test
    void testGetTradesByAccountReturnsNotFoundFromService() throws Exception {
        UUID accountId = UUID.randomUUID();

        when(tradeService.getAllTradesByAccountWithInstrument(accountId, null, null))
                .thenThrow(new ObjectNotFoundException("Account ID cannot be null"));

        mockMvc.perform(get("/api/trades/account/{accountId}", accountId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // Verifies account route returns 500 for invalid UUID path values.
    @Test
    void testGetTradesByAccountReturnsInternalServerErrorForInvalidUuid() throws Exception {
        mockMvc.perform(get("/api/trades/account/{accountId}", "not-a-uuid"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    // Verifies getting trades by account and instrument returns 200 with results.
    @Test
    void testGetTradesByAccountAndInstrumentReturnsOkWhenTradesExist() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        TradeResponse response = new TradeResponse(
                UUID.randomUUID(),
                accountId,
                instrumentId,
                "MSFT",
                "Microsoft Corp.",
                "SELL",
                BigDecimal.valueOf(3),
                BigDecimal.valueOf(200),
                BigDecimal.valueOf(600),
                OffsetDateTime.now()
        );

        when(tradeService.getTradesByAccountAndInstrument(accountId, instrumentId)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/trades/account/{accountId}/instrument/{instrumentId}", accountId, instrumentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value(accountId.toString()))
                .andExpect(jsonPath("$[0].instrumentId").value(instrumentId.toString()))
                .andExpect(jsonPath("$[0].ticker").value("MSFT"));
    }

    // Verifies getting trades by account and instrument returns 204 when empty.
    @Test
    void testGetTradesByAccountAndInstrumentReturnsNoContentWhenEmpty() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        when(tradeService.getTradesByAccountAndInstrument(accountId, instrumentId)).thenReturn(List.of());

        mockMvc.perform(get("/api/trades/account/{accountId}/instrument/{instrumentId}", accountId, instrumentId))
                .andExpect(status().isNoContent());
    }

    // Verifies getting trades by account and instrument returns 404 for service errors.
    @Test
    void testGetTradesByAccountAndInstrumentReturnsNotFoundFromService() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        when(tradeService.getTradesByAccountAndInstrument(accountId, instrumentId))
                .thenThrow(new ObjectNotFoundException("Instrument ID cannot be null"));

        mockMvc.perform(get("/api/trades/account/{accountId}/instrument/{instrumentId}", accountId, instrumentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // Verifies account+instrument route returns 500 for invalid UUID path values.
    @Test
    void testGetTradesByAccountAndInstrumentReturnsInternalServerErrorForInvalidUuid() throws Exception {
        mockMvc.perform(get("/api/trades/account/{accountId}/instrument/{instrumentId}", "not-a-uuid", "also-not-uuid"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }
}
