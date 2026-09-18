package com.neueda.leap.service;
import org.springframework.stereotype.Service;

import com.neueda.leap.dto.InstrumentResponse;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.repository.InstrumentRepository;

import java.util.List;
import java.util.Optional;

@Service
public class InstrumentService {

    private final InstrumentRepository instrumentRepository;

    public InstrumentService(InstrumentRepository instrumentRepository) {
        this.instrumentRepository = instrumentRepository;
    }

    public List<InstrumentResponse> getAllInstruments() {
        return instrumentRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public Optional<InstrumentResponse> getByTicker(String ticker) {
        return instrumentRepository.findByTicker(ticker)
                .map(this::toDto);
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