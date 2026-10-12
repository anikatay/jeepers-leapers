package com.neueda.leap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.dto.request.InstrumentRequest;
import com.neueda.leap.dto.response.InstrumentResponse;
import com.neueda.leap.exception.ObjectAlreadyExistsException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.service.InstrumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InstrumentController.class)
class InstrumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InstrumentService instrumentService;

    // Verifies creating an instrument returns 201 with payload fields.
    @Test
    void testCreateInstrumentReturnsCreatedForValidInput() throws Exception {
        InstrumentRequest request = new InstrumentRequest("XNYS", "AAPL", "Apple Inc.", BigDecimal.valueOf(195.25));
        UUID instrumentId = UUID.randomUUID();

        Instrument created = new Instrument();
        created.setInstrumentId(instrumentId);
        created.setTicker("AAPL");
        created.setName("Apple Inc.");
        created.setExchange("XNYS");
        created.setCurrentPrice(BigDecimal.valueOf(195.25));

        when(instrumentService.addInstrument(any(Instrument.class))).thenReturn(created);

        mockMvc.perform(post("/api/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instrumentId").value(instrumentId.toString()))
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.name").value("Apple Inc."))
                .andExpect(jsonPath("$.exchange").value("XNYS"))
                .andExpect(jsonPath("$.currentPrice").value(195.25));
    }

    // Verifies creating an instrument with invalid request body returns 400.
    @Test
    void testCreateInstrumentReturnsBadRequestForInvalidInput() throws Exception {
        String invalidPayload = "{\"exchangeId\":\"\",\"ticker\":\"\",\"name\":\"\",\"currentPrice\":0}";

        mockMvc.perform(post("/api/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // Verifies creating a duplicate instrument returns 409 conflict.
    @Test
    void testCreateInstrumentReturnsConflictForDuplicate() throws Exception {
        InstrumentRequest request = new InstrumentRequest("XNYS", "AAPL", "Apple Inc.", BigDecimal.valueOf(195.25));

        when(instrumentService.addInstrument(any(Instrument.class)))
                .thenThrow(new ObjectAlreadyExistsException("Creating this instrument would lead to duplicate instruments"));

        mockMvc.perform(post("/api/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    // Verifies create endpoint returns 500 for unexpected service failures.
    @Test
    void testCreateInstrumentReturnsInternalServerErrorForUnhandledFailure() throws Exception {
        InstrumentRequest request = new InstrumentRequest("XNYS", "AAPL", "Apple Inc.", BigDecimal.valueOf(195.25));

        when(instrumentService.addInstrument(any(Instrument.class)))
                .thenThrow(new RuntimeException("unexpected failure"));

        mockMvc.perform(post("/api/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    // Verifies listing instruments returns 200 with the collection payload.
    @Test
    void testGetAllInstrumentsReturnsOk() throws Exception {
        InstrumentResponse one = new InstrumentResponse(UUID.randomUUID(), "AAPL", "Apple Inc.", BigDecimal.valueOf(195.25), "XNYS");
        InstrumentResponse two = new InstrumentResponse(UUID.randomUUID(), "MSFT", "Microsoft Corp.", BigDecimal.valueOf(401.75), "XNAS");

        when(instrumentService.findAllInstruments()).thenReturn(List.of(one, two));

        mockMvc.perform(get("/api/instruments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[1].ticker").value("MSFT"));
    }

    // Verifies list endpoint returns 500 for unexpected service failures.
    @Test
    void testGetAllInstrumentsReturnsInternalServerErrorForUnhandledFailure() throws Exception {
        when(instrumentService.findAllInstruments()).thenThrow(new RuntimeException("read failed"));

        mockMvc.perform(get("/api/instruments"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    // Verifies getting by ticker returns 200 with instrument details.
    @Test
    void testGetInstrumentByTickerReturnsOkWhenFound() throws Exception {
        UUID instrumentId = UUID.randomUUID();
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);
        instrument.setTicker("AAPL");
        instrument.setName("Apple Inc.");
        instrument.setExchange("XNYS");
        instrument.setCurrentPrice(BigDecimal.valueOf(195.25));

        when(instrumentService.getInstrumentByTicker("AAPL")).thenReturn(instrument);

        mockMvc.perform(get("/api/instruments/{ticker}", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumentId").value(instrumentId.toString()))
                .andExpect(jsonPath("$.ticker").value("AAPL"));
    }

    // Verifies getting by ticker returns 404 when instrument does not exist.
    @Test
    void testGetInstrumentByTickerReturnsNotFoundWhenMissing() throws Exception {
        when(instrumentService.getInstrumentByTicker("MISSING"))
                .thenThrow(new ObjectNotFoundException("Instrument not found "));

        mockMvc.perform(get("/api/instruments/{ticker}", "MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // Verifies getting by ticker returns 404 for blank ticker values.
    @Test
    void testGetInstrumentByTickerReturnsNotFoundForBlankTicker() throws Exception {
        when(instrumentService.getInstrumentByTicker(" "))
                .thenThrow(new ObjectNotFoundException("Ticker cannot be empty"));

        mockMvc.perform(get("/api/instruments/{ticker}", " "))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // Verifies update price endpoint returns 204 for valid input.
    @Test
    void testUpdateInstrumentPriceReturnsNoContentWhenValid() throws Exception {
        UUID instrumentId = UUID.randomUUID();

        doNothing().when(instrumentService).updatePrice(instrumentId, BigDecimal.valueOf(210.50));

        mockMvc.perform(patch("/api/instruments/{instrumentId}/price", instrumentId)
                        .param("price", "210.50"))
                .andExpect(status().isNoContent());
    }

    // Verifies update price endpoint returns 400 for invalid UUID values.
    @Test
    void testUpdateInstrumentPriceReturnsBadRequestForInvalidUuid() throws Exception {
        mockMvc.perform(patch("/api/instruments/{instrumentId}/price", "not-a-uuid")
                        .param("price", "210.50"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

        // Verifies update price endpoint returns 500 when price parameter is missing.
    @Test
        void testUpdateInstrumentPriceReturnsInternalServerErrorWhenPriceMissing() throws Exception {
        UUID instrumentId = UUID.randomUUID();

        mockMvc.perform(patch("/api/instruments/{instrumentId}/price", instrumentId))
                                .andExpect(status().isInternalServerError())
                                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    // Verifies update price endpoint returns 404 when instrument is missing.
    @Test
    void testUpdateInstrumentPriceReturnsNotFoundWhenInstrumentMissing() throws Exception {
        UUID instrumentId = UUID.randomUUID();

        doThrow(new ObjectNotFoundException("Instrument not found: " + instrumentId))
                .when(instrumentService).updatePrice(eq(instrumentId), any(BigDecimal.class));

        mockMvc.perform(patch("/api/instruments/{instrumentId}/price", instrumentId)
                        .param("price", "210.50"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // Verifies update price endpoint returns 500 for known application failures.
    @Test
    void testUpdateInstrumentPriceReturnsInternalServerErrorForProcessingFailure() throws Exception {
        UUID instrumentId = UUID.randomUUID();

        doThrow(new ObjectNotProcessedException("Instrument was unable to be updated"))
                .when(instrumentService).updatePrice(eq(instrumentId), any(BigDecimal.class));

        mockMvc.perform(patch("/api/instruments/{instrumentId}/price", instrumentId)
                        .param("price", "210.50"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }
}
