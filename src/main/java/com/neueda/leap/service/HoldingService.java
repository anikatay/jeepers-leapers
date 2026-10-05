package com.neueda.leap.service;

import com.neueda.leap.dto.request.HoldingRequest;
import com.neueda.leap.dto.response.HoldingResponse;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.mapper.HoldingMapper;
import com.neueda.leap.model.Holding;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class HoldingService implements IService {

    private final HoldingMapper holdingMapper;

    public HoldingService(HoldingMapper holdingMapper) {
        this.holdingMapper = holdingMapper;
    }

    public List<HoldingResponse> getHoldingsByAccountId(UUID accountID) {
        List<Holding> response = holdingMapper.getHoldingsByAccountId(accountID);

        if (response.isEmpty()) {
            return null;
        }

        List<HoldingResponse> holdingDtos = response.stream()
                .map(h -> new HoldingResponse(
                    h.getAccountId(),
                    h.getInstrumentId(),
                    h.getQuantity()
                ))
                .toList();

        return holdingDtos;
    }

    public HoldingResponse getHoldingByAccountIdAndInstrumentId(UUID accountId, UUID instrumentId) {
        Holding holding = holdingMapper.getHoldingByAccountIdAndInstrumentId(accountId, instrumentId);

        if (holding == null) {
            return null;
        }

        return new HoldingResponse(
                holding.getAccountId(),
                holding.getInstrumentId(),
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
        
        return new HoldingResponse(
                holding.getAccountId(),
                holding.getInstrumentId(),
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
        int updatHolding = holdingMapper.updateHoldingQuantity(holding.getAccountId(),holding.getInstrumentId(), newQuantity);
        if(updatHolding < 1){
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
        int newQuantity = holding.getQuantity() - amount;
        int updatHolding = holdingMapper.updateHoldingQuantity(holding.getAccountId(),holding.getInstrumentId(), newQuantity);
        if(updatHolding < 1){
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

        return new HoldingResponse(
            holding.getAccountId(),
            holding.getInstrumentId(),
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