package com.neueda.leap.service;

import com.neueda.leap.dto.response.TradeResponse;
import com.neueda.leap.mapper.TradeMapper;
import com.neueda.leap.model.Trade;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TradeService implements IService {

    private final TradeMapper tradeMapper;
    private final AccountService accountService;
    private final InstrumentService instrumentService;
    
    public TradeService(TradeMapper tradeMapper, AccountService accountService, InstrumentService instrumentService) {
        this.tradeMapper = tradeMapper;
        this.accountService = accountService;
        this.instrumentService = instrumentService;
    }
    
    @Transactional 
    public Trade createTrade(Trade trade){
        if(trade == null){
            throw new IllegalArgumentException("Trade cannot be null");
        }
        if(trade.getAccountId() == null){
            throw new IllegalArgumentException("Account ID cannot be null");
        }

        if(trade.getInstrumentId() == null){
            throw new IllegalArgumentException("Instrument ID cannot be null");
        }
        instrumentService.getInstrumentById(trade.getInstrumentId());
        

        if(trade.getSide() == null){
            throw new IllegalArgumentException("Side cannot be null");
        }

        if(trade.getQuantity() <= 0){
            throw new IllegalArgumentException("Quantity must be non 0");
        }

        if(trade.getExecutionPrice() == null){
            throw new IllegalArgumentException("Execution price cannot be null");
        }

        if(trade.getExecutedAt() == null){
            trade.setExecutedAt(OffsetDateTime.now());
        }
        if(trade.getTradeValue() == null){
            BigDecimal tradeValue = trade.getExecutionPrice().multiply(BigDecimal.valueOf(trade.getQuantity()));
            trade.setTradeValue(tradeValue);
        }
        tradeMapper.createTrade(trade);
        if(trade.getSide().equals("BUY")){
            accountService.decrementBalance(trade.getAccountId(), trade.getTradeValue());
        }else if(trade.getSide().equals("SELL")){
            accountService.incrementBalance(trade.getAccountId(), trade.getTradeValue());
        }
        return trade;
    }

    public List<TradeResponse> getTradesByAccountAndInstrument(UUID accountId, UUID instrumentId) {
        if(accountId == null){
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if(instrumentId == null){
            throw new IllegalArgumentException("Instrument ID cannot be null");
        }
        return tradeMapper.findTradesByAccountAndInstrument(accountId, instrumentId);
    }


    public List<TradeResponse> getAllTradesByAccountWithInstrument(UUID accountId){
        if(accountId == null){
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        return tradeMapper.findAllTradesForAccountWithInstrument(accountId);
    }

}