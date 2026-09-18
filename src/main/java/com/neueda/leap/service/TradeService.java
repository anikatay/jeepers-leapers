package com.neueda.leap.service;

import com.neueda.leap.dto.TradeResponse;
import com.neueda.leap.model.Trade;
import com.neueda.leap.repository.TradeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TradeService implements IService {

    private final TradeRepository tradeRepository;

    public TradeService(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    public List<TradeResponse> getTradesForUser(UUID userId) {
        List<Trade> trades = tradeRepository.findByAccountUserUserId(userId);

        if (trades.isEmpty()) {
            return List.of();
        }

        List<TradeResponse> response = trades.stream()
                .map(t -> new TradeResponse(
                        t.getTradeId(),
                        t.getInstrument().getName(),
                        t.getInstrument().getTicker(),
                        t.getSide(),
                        t.getQuantity(),
                        t.getExecutionPrice(),
                        t.getTradeValue(),
                        t.getExecutedAt()))
                .toList();

        return response;
    }
}