package com.neueda.leap.controller;

import com.neueda.leap.dto.TradeResponse;
import com.neueda.leap.model.Trade;
import com.neueda.leap.repository.TradeRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * Admin endpoint: returns all trades made on the platform.
 */
@RestController
@RequestMapping("/api")
public class TradeController {

    private final TradeRepository tradeRepository;

    public TradeController(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    @GetMapping("/trades")
    public List<TradeResponse> getAllTrades() {
        List<Trade> trades = tradeRepository.findAll();

        return trades.stream()
                .map(t -> new TradeResponse(
                        t.getUser().getEmail(),
                        t.getInstrument().getName(),
                        t.getTradeValue()))
                .toList();
    }
}
