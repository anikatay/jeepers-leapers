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

    public HoldingResponse createHolding(HoldingResponse holdingResponse) {
        
        return holdingResponse;
    }

}