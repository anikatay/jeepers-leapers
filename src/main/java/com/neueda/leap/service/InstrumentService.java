package com.neueda.leap.service;
import org.springframework.stereotype.Service;

import com.neueda.leap.dto.response.InstrumentResponse;
import com.neueda.leap.exception.ObjectAlreadyExistsException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectNotProcessedException;
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
            throw new ObjectNotFoundException("Instrument ID cannot be null");
        }
        Instrument instrument = instrumentMapper.findById(instrumentId);
        if(instrument == null){
            throw new ObjectNotFoundException("Instrument not found: " + instrumentId);
        }
        return instrument;
    }

    public Instrument getInstrumentByTicker(String ticker) {
        if(ticker == null || ticker.isEmpty()) {
            throw new ObjectNotFoundException("Ticker cannot be empty");
        }
        Instrument instrument = instrumentMapper.findByTicker(ticker);
        if(instrument == null){
            throw new ObjectNotFoundException("Instrument not found ");
        }
        return instrument;
    }

    @Transactional
    public Instrument addInstrument(Instrument instrument) {
        if(instrument.getInstrumentId() == null){
            throw new ObjectNotFoundException("Instrument ID cannot be null");
        }
        if(instrumentMapper.findById(instrument.getInstrumentId()) != null){
            throw new ObjectAlreadyExistsException("Creating this instrument would lead to duplicate instruments");
        }
        if(instrument.getTicker() == null){
            throw new ObjectNotFoundException("Ticker cannot be empty");
        }
        if(instrumentMapper.findByTicker(instrument.getTicker()) != null){
            throw new ObjectAlreadyExistsException("Creating this instrument would lead to duplicate instruments");
        }
        if(instrument.getExchange() == null){
            throw new ObjectNotFoundException("Exchange ID cannot be null");
        }
        if(instrument.getCurrentPrice().compareTo(BigDecimal.ZERO) <= 0){
            throw new ObjectNotFoundException("Current price cannot be negeative");
        }
        
        
        if(instrument.getUpdatedAt() == null){
            instrument.setUpdatedAt(OffsetDateTime.now());
        }
        int instrumentAddedFlag = instrumentMapper.addInstrument(instrument);
        if(instrumentAddedFlag < 1){
            throw new ObjectNotProcessedException("Could Not add this instrument " + instrument);
        }
        return instrument;
    }

    @Transactional 
    public void updatePrice(UUID instrumentId, BigDecimal price){
        Instrument instrument = getInstrumentById(instrumentId);
        instrument.setCurrentPrice(price);
        instrument.setUpdatedAt(OffsetDateTime.now());
        int instrumentUpdateFlag = instrumentMapper.updatePrice(instrument);
        if(instrumentUpdateFlag < 1){
            throw new ObjectNotProcessedException("Instrument was unable to be updated " + instrument);
        }
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