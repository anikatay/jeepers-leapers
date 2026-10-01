package com.neueda.leap.service;
import org.springframework.stereotype.Service;

import com.neueda.leap.dto.response.InstrumentResponse;
import com.neueda.leap.exception.InstrumentAlreadyExistsException;
import com.neueda.leap.exception.InstrumentNotFoundException;
import com.neueda.leap.mapper.InstrumentMapper;
import com.neueda.leap.model.Instrument;

import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class InstrumentService implements IService {

    private final InstrumentMapper instrumentMapper;

    public InstrumentService(InstrumentMapper instrumentMapper) {
        this.instrumentMapper = instrumentMapper;
    }

    public Instrument getInstrumentById(UUID instrumentId){
        if(instrumentId == null){
            throw new IllegalArgumentException("Instrument ID cannot be null");
        }
        Instrument instrument = instrumentMapper.findById(instrumentId);
        if(instrument == null){
            throw new InstrumentNotFoundException("Instrument not found: " + instrumentId);
        }
        return instrument;
    }

    public Instrument getInstrumentByTicker(String ticker) {
        if(ticker == null || ticker.isEmpty()) {
            throw new IllegalArgumentException("Ticker cannot be empty");
        }
        Instrument instrument = instrumentMapper.findByTicker(ticker);
        if(instrument == null){
            throw new InstrumentNotFoundException("Instrument not found ");
        }
        return instrument;
    }

    @Transactional
    public Instrument addInstrument(Instrument instrument) {
        if(instrument.getInstrumentId() == null){
            throw new IllegalArgumentException("Instrument ID cannot be null");
        }
        if(instrumentMapper.findById(instrument.getInstrumentId()) != null){
            throw new InstrumentAlreadyExistsException("Creating this instrument would lead to duplicate instruments");
        }
        if(instrument.getTicker() == null){
            throw new IllegalArgumentException("Ticker cannot be empty");
        }
        if(instrumentMapper.findByTicker(instrument.getTicker()) != null){
            throw new InstrumentAlreadyExistsException("Creating this instrument would lead to duplicate instruments");
        }
        if(instrument.getExchange() == null){
            throw new IllegalArgumentException("Exchange ID cannot be null");
        }
        if(instrument.getCurrentPrice().compareTo(BigDecimal.ZERO) <= 0){
            throw new IllegalArgumentException("Current price cannot be negeative");
        }
        
        
        if(instrument.getUpdatedAt() == null){
            instrument.setUpdatedAt(OffsetDateTime.now());
        }
        instrumentMapper.addInstrument(instrument);
        return instrument;
    }

    @Transactional 
    public void updatePrice(UUID instrumentId, BigDecimal price){
        Instrument instrument = getInstrumentById(instrumentId);
        instrument.setCurrentPrice(price);
        instrument.setUpdatedAt(OffsetDateTime.now());
        instrumentMapper.updatePrice(instrument);
    }
    
    public List<InstrumentResponse> findAllInstruments(){
        List<Instrument> instruments = instrumentMapper.findAll();
        return instruments.stream()
                .map(i -> new InstrumentResponse(
                        i.getInstrumentId(),
                        i.getTicker(),
                        i.getName(),
                        i.getCurrentPrice(),
                        i.getExchange()))
                .toList();
    }

}