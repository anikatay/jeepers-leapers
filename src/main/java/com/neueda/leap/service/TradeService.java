package com.neueda.leap.service;

import com.neueda.leap.dto.response.TradeResponse;
import com.neueda.leap.event.TradeExecutedEvent;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.mapper.TradeMapper;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Holding;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.Trade;
import java.time.Duration;
import java.time.OffsetDateTime;
import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class TradeService implements IService {

    private final TradeMapper tradeMapper;
    private final AccountService accountService;
    private final InstrumentService instrumentService;
    private final HoldingService holdingService;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private final ApplicationEventPublisher eventPublisher;
    @Value("${trade.max-price-age-seconds:180}")
    private long maxPriceAgeSeconds;

    
    private void assertPriceFresh(Instrument instrument) {
        if (instrument.getUpdatedAt() == null) {
            throw new ObjectInvalidException("Quote timestamp missing; refresh and retry");
        }
        long age = Duration.between(instrument.getUpdatedAt(), OffsetDateTime.now()).getSeconds();
        if (age > maxPriceAgeSeconds) {
            throw new ObjectInvalidException("Quote is stale; refresh and retry");
        }
    }

    public TradeService(TradeMapper tradeMapper, AccountService accountService, InstrumentService instrumentService, HoldingService holdingService, ApplicationEventPublisher eventPublisher) {
        this.tradeMapper = tradeMapper;
        this.accountService = accountService;
        this.instrumentService = instrumentService;
        this.holdingService = holdingService;
        this.eventPublisher = eventPublisher;
    }
    
    @Transactional 
    public Trade createTrade(Trade trade){
        if(trade == null){
            throw new ObjectInvalidException("Trade cannot be null");
        }
        if(trade.getAccountId() == null){
            throw new ObjectInvalidException("Account ID cannot be null");
        }
        if(trade.getInstrumentId() == null){
            throw new ObjectInvalidException("Instrument ID cannot be null");
        }
        final Instrument instrument = instrumentService.refreshPriceIfStale(trade.getInstrumentId());
        if(instrument == null){
            throw new ObjectNotFoundException("This instrument does not exist");
        }
        
        assertPriceFresh(instrument);
        trade.setExecutionPrice(instrument.getCurrentPrice());
        if(!"BUY".equals(trade.getSide()) && !"SELL".equals(trade.getSide())){
            throw new ObjectInvalidException("Side cannot be null");
        }

        if(trade.getQuantity() == null || trade.getQuantity().compareTo(BigDecimal.ZERO) <= 0){
            throw new ObjectInvalidException("Quantity must be greater than 0");
        }

        if(trade.getExecutionPrice() == null){
            throw new ObjectNotFoundException("Execution price cannot be null");
        }

        if(trade.getExecutedAt() == null){
            trade.setExecutedAt(OffsetDateTime.now());
        }
        if(trade.getTradeValue() == null){
            BigDecimal tradeValue = trade.getExecutionPrice().multiply(trade.getQuantity());
            trade.setTradeValue(tradeValue);
        }
        int tradeCreatedFlag = tradeMapper.createTrade(trade);
        if(tradeCreatedFlag < 1){
            throw new ObjectNotProcessedException("Could not create trade");
        }
    
        if(trade.getSide().equals("BUY")){

            Holding holding = holdingService.findHolding(trade.getAccountId(), instrument.getInstrumentId());
            
            try{
                accountService.decrementBalance(trade.getAccountId(), trade.getTradeValue());
            }catch(Exception e){
                throw new ObjectInvalidException("Insufficient balance for trade",e);
            }
            if(holding == null){
                holding = new Holding(
                    trade.getAccountId(),
                    instrument.getInstrumentId(),
                    trade.getQuantity()
                );
                holdingService.createHoldingInternal(holding);
            }else{
                holdingService.incrementHoldingQuantityImternal(holding, trade.getQuantity());
            }
        }else if(trade.getSide().equals("SELL")){
            Holding holding = holdingService.findHolding(trade.getAccountId(), instrument.getInstrumentId());
            if(holding == null){
                throw new ObjectInvalidException("No holding exits for this instrument");
            }
            if(holding.getQuantity().compareTo(trade.getQuantity()) < 0){
                throw new ObjectInvalidException("Insufficient quantity to sell");
            }
            holdingService.decrementHoldingQuantityImternal(holding, trade.getQuantity());
            accountService.incrementBalance(trade.getAccountId(), trade.getTradeValue());
        }
        
        TradeExecutedEvent event = new TradeExecutedEvent(
            trade.getTradeId(),
            trade.getAccountId(),
            trade.getInstrumentId(),
            trade.getSide(),
            trade.getQuantity(),
            trade.getExecutionPrice(),
            trade.getTradeValue(),
            trade.getExecutedAt()
        );
        eventPublisher.publishEvent(event);

        return trade;
    }

    

    public List<TradeResponse> getTradesByAccountAndInstrument(UUID accountId, UUID instrumentId) {
        if(accountId == null){
            throw new ObjectNotFoundException("Account ID cannot be null");
        }
        if(instrumentId == null){
            throw new ObjectNotFoundException("Instrument ID cannot be null");
        }
        return tradeMapper.findTradesByAccountAndInstrument(accountId, instrumentId);
    }


    public List<TradeResponse> getAllTradesByAccountWithInstrument(UUID accountId, Integer limit, Integer offset){
        if(accountId == null){
            throw new ObjectNotFoundException("Account ID cannot be null");
        }
        int safeLimit = (limit == null) ? DEFAULT_PAGE_SIZE : Math.min(limit, MAX_PAGE_SIZE);
        int safeOffset = (offset == null) ? 0 : Math.max(offset, 0);

        return tradeMapper.findAllTradesForAccountWithInstrument(accountId, safeLimit, safeOffset);
    }

}