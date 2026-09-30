package com.neueda.leap.service;

import com.neueda.leap.dto.response.HoldingResponse;
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

    public HoldingResponse createHolding(HoldingRequest request) {
        // TODO: verify trade success first
        Holding createdHolding = holdingMapper.insertHolding(
            request.accountId(),
            request.instrumentId(),
            request.quantity()
        );

        if( createdHolding == null ) {
            return null;
        }
        
        return new HoldingResponse(
                createdHolding.getAccountId(),
                createdHolding.getInstrumentId(),
                createdHolding.getQuantity()
        );
    }

    public HoldingResponse updateHoldingQuantity(HoldingRequest request) {
        // TODO: verify trade success first
        Holding updatedHolding = holdingMapper.updateHoldingQuantity(
            request.accountId(),
            request.instrumentId(),
            request.quantity()
        );

        if (updatedHolding == null) {
            return null;
        }

        return new HoldingResponse(
                updatedHolding.getAccountId(),
                updatedHolding.getInstrumentId(),
                updatedHolding.getQuantity()
        );
    }

    public void deleteHolding(UUID accountId, UUID instrumentId) {
        holdingMapper.deleteHolding(accountId, instrumentId);
    }

    public void deleteHoldingsByAccountId(UUID accountId) {
        holdingMapper.deleteHoldingsByAccountId(accountId);
    }

}