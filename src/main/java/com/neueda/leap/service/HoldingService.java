package com.neueda.leap.service;

import com.neueda.leap.dto.HoldingResponse;
import com.neueda.leap.dto.HoldingResponse.HoldingDto;
import com.neueda.leap.model.Holding;
import com.neueda.leap.repository.HoldingRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class HoldingService implements IService {

    private final HoldingRepository holdingRepository;

    public HoldingService(HoldingRepository holdingRepository) {
        this.holdingRepository = holdingRepository;
    }

    public HoldingResponse getPortfolio(UUID userId) {
        List<Holding> response = holdingRepository.findByAccountUserId(userId);

        if (response.isEmpty()) {
            return new HoldingResponse(BigDecimal.ZERO, List.of());
        }

        List<HoldingDto> holdingDtos = response.stream()
                .map(h -> {
                    BigDecimal holdingValue = h.getQuantity()
                            .multiply(h.getInstrument().getCurrentPrice());
                    return new HoldingDto(
                            h.getInstrument().getName(),
                            h.getInstrument().getTicker(),
                            h.getQuantity(),
                            holdingValue);
                })
                .toList();

        BigDecimal totalValue = holdingDtos.stream()
                .map(HoldingDto::getHoldingValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new HoldingResponse(totalValue, holdingDtos);
    }

    public HoldingResponse getHoldingsByAccount(UUID accountId) {
        List<Holding> response = holdingRepository.findByAccountId(accountId);

        if (response.isEmpty()) {
            return new HoldingResponse(BigDecimal.ZERO, List.of());
        }

        List<HoldingDto> holdingDtos = response.stream()
                .map(h -> {
                    BigDecimal holdingValue = h.getQuantity()
                            .multiply(h.getInstrument().getCurrentPrice());
                    return new HoldingDto(
                            h.getInstrument().getName(),
                            h.getInstrument().getTicker(),
                            h.getQuantity(),
                            holdingValue);
                })
                .toList();

        BigDecimal totalValue = holdingDtos.stream()
                .map(HoldingDto::getHoldingValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new HoldingResponse(totalValue, holdingDtos);
    }
}