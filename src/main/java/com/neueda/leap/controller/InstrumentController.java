package com.neueda.leap.controller;

import com.neueda.leap.dto.request.InstrumentRequest;
import com.neueda.leap.dto.response.InstrumentResponse;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.service.InstrumentService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {
 
    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @PostMapping
    public ResponseEntity<InstrumentResponse> createInstrument(
            @Valid @RequestBody InstrumentRequest request) {
        
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(UUID.randomUUID());
        instrument.setTicker(request.ticker());
        instrument.setName(request.name());
        instrument.setExchange(request.exchangeId());
        instrument.setCurrentPrice(request.currentPrice());
        
        Instrument created = instrumentService.addInstrument(instrument);
        
        InstrumentResponse response = new InstrumentResponse(
                created.getInstrumentId(),
                created.getTicker(),
                created.getName(),
                created.getCurrentPrice(),
                created.getExchange());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping
    public ResponseEntity<List<InstrumentResponse>> getAllInstruments() {
        List<InstrumentResponse> instruments = instrumentService.findAllInstruments();
        return ResponseEntity.ok(instruments);
    }

    @GetMapping("/{ticker}")
    public ResponseEntity<InstrumentResponse> getInstrumentByTicker(@PathVariable String ticker) {
        
        Instrument instrument = instrumentService.getInstrumentByTicker(ticker);
        InstrumentResponse response = new InstrumentResponse(
                instrument.getInstrumentId(),
                instrument.getTicker(),
                instrument.getName(),
                instrument.getCurrentPrice(),
                instrument.getExchange());
        return ResponseEntity.ok(response);
        
    }

    @PatchMapping("/{instrumentId}/price")
    public ResponseEntity<Void> updateInstrumentPrice(
            @PathVariable UUID instrumentId,
            @RequestParam java.math.BigDecimal price) {
        
        instrumentService.updatePrice(instrumentId, price);
        return ResponseEntity.noContent().build();
    }

}