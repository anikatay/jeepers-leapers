package com.neueda.leap.service;

import com.neueda.leap.dto.response.HoldingResponse;
import com.neueda.leap.dto.response.HoldingResponse.HoldingDto;
import com.neueda.leap.mapper.HoldingMapper;
import com.neueda.leap.model.Holding;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class HoldingService implements IService {

    private final HoldingMapper holdingMapper;

    public HoldingService(HoldingMapper holdingMapper) {
        this.holdingMapper = holdingMapper;
    }

    public HoldingResponse getHoldings(UUID userId) {
        List<Holding> response = holdingMapper.getHoldingsByAccountId(userId);

        if (response.isEmpty()) {
            return new HoldingResponse(BigDecimal.ZERO, List.of());
        }

        List<HoldingDto> holdingDtos = response.stream()
                .map(h -> {
                    
                })
                .toList();

        BigDecimal totalValue = holdingDtos.stream()
                .map(HoldingDto::getHoldingValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new HoldingResponse(totalValue, holdingDtos);
    }

    public HoldingResponse createHolding(HoldingResponse holdingResponse) {
        
        return holdingResponse;
    }

}