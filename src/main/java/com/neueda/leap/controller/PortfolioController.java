package com.neueda.leap.controller;

import com.neueda.leap.dto.PortfolioResponse;
import com.neueda.leap.dto.PortfolioResponse.HoldingDto;
import com.neueda.leap.model.Holding;
import com.neueda.leap.repository.HoldingRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class PortfolioController {

    private final HoldingRepository holdingRepository;

    public PortfolioController(HoldingRepository holdingRepository) {
        this.holdingRepository = holdingRepository;
    }

    @GetMapping("/portfolio/{userId}")
    public ResponseEntity<PortfolioResponse> getPortfolio(@PathVariable UUID userId) {
        List<Holding> holdings = holdingRepository.findByAccountUserId(userId);

        if (holdings.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<HoldingDto> holdingDtos = holdings.stream()
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

        return ResponseEntity.ok(new PortfolioResponse(totalValue, holdingDtos));
    }
}