package com.neueda.leap.service;

import com.neueda.leap.dto.response.TradeResponse;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.mapper.TradeMapper;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Holding;
import com.neueda.leap.model.Instrument;
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
    private final HoldingService holdingService;
    
    public TradeService(TradeMapper tradeMapper, AccountService accountService, InstrumentService instrumentService, HoldingService holdingService) {
        this.tradeMapper = tradeMapper;
        this.accountService = accountService;
        this.instrumentService = instrumentService;
        this.holdingService = holdingService;
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
        final Instrument instrument = instrumentService.getInstrumentById(trade.getInstrumentId());
        if(instrument == null){
            throw new ObjectNotFoundException("This instrument does not exist");
        }

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
        int tradeCreatedFlag = tradeMapper.createTrade(trade);
        if(tradeCreatedFlag < 1){
            throw new ObjectNotProcessedException("Could not create trade");
        }
    
        if(trade.getSide().equals("BUY")){
            Account account = accountService.getAccount(trade.getAccountId());
            Holding holding = holdingService.findHolding(trade.getAccountId(), instrument.getInstrumentId());
            
            if(account.getBalance().compareTo(trade.getTradeValue()) < 0){
                throw new ObjectInvalidException("Insufficient balance for trade");
            }
            if(holding == null){
                holding = new Holding(
                    trade.getAccountId(),
                    instrument.getInstrumentId(),
                    trade.getQuantity()
                );
                holdingService.createHoldingInternal(holding);
            }else if(holding != null){
               holdingService.incrementHoldingQuantityImternal(holding, trade.getQuantity());
            }
            accountService.decrementBalance(trade.getAccountId(), trade.getTradeValue());
        }else if(trade.getSide().equals("SELL")){
            Holding holding = holdingService.findHolding(trade.getAccountId(), instrument.getInstrumentId());
            if(holding == null){
                throw new ObjectInvalidException("No holding exits for this instrument");
            }
            if(holding.getQuantity() < trade.getQuantity()){
                throw new ObjectInvalidException("INsufficient quantity to sell");
            }
            holdingService.decrementHoldingQuantityImternal(holding, trade.getQuantity());
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