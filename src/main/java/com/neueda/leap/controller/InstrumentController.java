package com.neueda.leap.controller;

import com.neueda.leap.dto.InstrumentResponse;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.repository.InstrumentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {

    private final InstrumentRepository instrumentRepository;

    public InstrumentController(InstrumentRepository instrumentRepository) {
        this.instrumentRepository = instrumentRepository;
    }

    @GetMapping
    public ResponseEntity<List<InstrumentResponse>> getAllInstruments() {
        List<InstrumentResponse> response = instrumentRepository.findAll().stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{ticker}")
    public ResponseEntity<InstrumentResponse> getByTicker(@PathVariable String ticker) {
        return instrumentRepository.findByTicker(ticker)
                .map(i -> ResponseEntity.ok(toDto(i)))
                .orElse(ResponseEntity.notFound().build());
    }

    private InstrumentResponse toDto(Instrument i) {
        return new InstrumentResponse(
                i.getInstrumentId(),
                i.getTicker(),
                i.getName(),
                i.getCurrentPrice(),
                i.getExchange().getExchangeId());
    }
}