package com.neueda.leap.controller;

import com.neueda.leap.dto.PortfolioResponse;
import com.neueda.leap.dto.PortfolioResponse.HoldingDto;
import com.neueda.leap.model.Position;
import com.neueda.leap.repository.PositionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Customer endpoint: returns a user's portfolio (instrument names + holding values).
 */
@RestController
@RequestMapping("/api")
public class PortfolioController {

    private final PositionRepository positionRepository;

    public PortfolioController(PositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    @GetMapping("/portfolio/{userId}")
    public ResponseEntity<PortfolioResponse> getPortfolio(@PathVariable UUID userId) {
        List<Position> positions = positionRepository.findByUserUserId(userId);

        if (positions.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<HoldingDto> holdings = positions.stream()
                .map(p -> {
                    BigDecimal holdingValue = p.getQuantity()
                            .multiply(p.getInstrument().getCurrentPrice());
                    return new HoldingDto(
                            p.getInstrument().getName(),
                            holdingValue);
                })
                .toList();

        BigDecimal totalValue = holdings.stream()
                .map(HoldingDto::getHoldingValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PortfolioResponse response = new PortfolioResponse(totalValue, holdings);
        return ResponseEntity.ok(response);
    }
}

