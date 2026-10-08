package com.neueda.leap.service;

import com.neueda.leap.dto.request.HoldingRequest;
import com.neueda.leap.dto.response.HoldingResponse;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.mapper.HoldingMapper;
import com.neueda.leap.model.Holding;
import com.neueda.leap.model.Instrument;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class HoldingService implements IService {

    private final HoldingMapper holdingMapper;
    private final InstrumentService instrumentService;

    public HoldingService(HoldingMapper holdingMapper, InstrumentService instrumentService) {
        this.holdingMapper = holdingMapper;
        this.instrumentService = instrumentService;
    }

    public List<HoldingResponse> getHoldingsByAccountId(UUID accountID) {
        List<Holding> response = holdingMapper.getHoldingsByAccountId(accountID);

        if (response.isEmpty()) {
            throw new ObjectNotFoundException("No holdings found for account");
        }

        List<HoldingResponse> holdingDtos = response.stream()
                .map(h ->{
                    Instrument instrument = instrumentService.getInstrumentById(h.getInstrumentId());
                    return new HoldingResponse(
                        h.getAccountId(),
                        h.getInstrumentId(),
                        instrument.getTicker(),
                        h.getQuantity()
                    );
                })
                .toList();

        return holdingDtos;
    }

    public HoldingResponse getHoldingByAccountIdAndInstrumentId(UUID accountId, UUID instrumentId) {
        Holding holding = holdingMapper.getHoldingByAccountIdAndInstrumentId(accountId, instrumentId);
        if (holding == null) {
            throw new ObjectNotFoundException("Respomse cannot be healthy");
        }
        
        Instrument instrument = instrumentService.getInstrumentById(holding.getInstrumentId());
        return new HoldingResponse(
                holding.getAccountId(),
                holding.getInstrumentId(),
                instrument.getTicker(),
                holding.getQuantity()
        );
    }

    public Holding findHolding(UUID accountId, UUID instrumentId){
        return holdingMapper.getHoldingByAccountIdAndInstrumentId(accountId, instrumentId);
    }

     
    public Holding createHoldingInternal(Holding holding) {
        int createdHolding = holdingMapper.insertHolding(holding.getAccountId(),holding.getInstrumentId(),holding.getQuantity());

        if( createdHolding < 1 ) {
            throw new ObjectNotProcessedException("Holding could not be created");
        }
        return holding;
    }

    public HoldingResponse createHolding(HoldingRequest request) {
        Holding holding = new Holding(
            request.accountId(),
            request.instrumentId(),
            request.quantity()
        );

        int createdHolding = holdingMapper.insertHolding(holding.getAccountId(),holding.getInstrumentId(),holding.getQuantity());
        if( createdHolding < 1  ) {
            throw new ObjectNotProcessedException("Holding could not be created");
        }
        Instrument instrument = instrumentService.getInstrumentById(holding.getInstrumentId());
        return new HoldingResponse(
                holding.getAccountId(),
                holding.getInstrumentId(),
                instrument.getTicker(),
                holding.getQuantity()
        );
    }
   
    public int incrementHoldingQuantityImternal(Holding holding, int amount){
        if(holding == null){
            throw new ObjectInvalidException("Holding cannot be Null");
        }
        if(amount <= 0){
            throw new ObjectInvalidException("amount cannot be less than 1");
        }
        int newQuantity = holding.getQuantity() + amount;
        int updateHolding = holdingMapper.updateHoldingQuantity(holding.getAccountId(),holding.getInstrumentId(), newQuantity);
        if(updateHolding < 1){
            throw new ObjectNotProcessedException("Was unable to update holding ");
        }
        return 1;
    }

    public int decrementHoldingQuantityImternal(Holding holding, int amount){
        if(holding == null){
            throw new ObjectInvalidException("Holding cannot be Null");
        }
        if(amount <= 0){
            throw new ObjectInvalidException("amount cannot be less than 1");
        }
        if(amount > holding.getQuantity()){
            throw new ObjectInvalidException("amount cannot be more than quantity");
        }
        final int newQuantity = holding.getQuantity() - amount;
        int updateHolding;
        if(newQuantity != 0){
            updateHolding = holdingMapper.updateHoldingQuantity(holding.getAccountId(),holding.getInstrumentId(), newQuantity);
        }else{
            updateHolding = holdingMapper.deleteHolding(holding.getAccountId(),holding.getInstrumentId());
        }

        if(updateHolding < 1){
            throw new ObjectNotProcessedException("Was unable to update holding ");
        }
        return 1;
    }

    public HoldingResponse updateHoldingQuantity(HoldingRequest request) {
        // TODO: verify trade success first
        Holding holding = new Holding(
            request.accountId(),
            request.instrumentId(),
            request.quantity()
        );
        int updatedHolding = holdingMapper.updateHoldingQuantity(
            holding.getAccountId(),
            holding.getInstrumentId(),
            holding.getQuantity()
        );

        if (updatedHolding < 1) {
            throw new ObjectNotProcessedException("Was unable to update holding");
        }
        Instrument instrument = instrumentService.getInstrumentById(holding.getInstrumentId());

        return new HoldingResponse(
            holding.getAccountId(),
            holding.getInstrumentId(),
            instrument.getTicker(),
            holding.getQuantity()
        );
    }

    public void deleteHolding(UUID accountId, UUID instrumentId) {
        holdingMapper.deleteHolding(accountId, instrumentId);
    }

    public void deleteHoldingsByAccountId(UUID accountId) {
        holdingMapper.deleteHoldingsByAccountId(accountId);
    }

}