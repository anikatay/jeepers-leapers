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
    private final MarketSessionService marketSessionService;

    private final ApplicationEventPublisher eventPublisher;
    @Value("${trade.max-price-age-seconds:180}")
    private long maxPriceAgeSeconds;

    
    private void assertPriceFresh(Instrument instrument) {
        if (instrument.getUpdatedAt() == null) {
            throw new ObjectInvalidException("Unable to make trade at the moment; quote unavailable");
        }
        Duration age = Duration.between(instrument.getUpdatedAt(), OffsetDateTime.now());
        if (age.isNegative() || age.compareTo(Duration.ofSeconds(maxPriceAgeSeconds)) > 0) {
            throw new ObjectInvalidException("Unable to make trade at the moment; quote is too old");
        }
    }

    public TradeService(TradeMapper tradeMapper, AccountService accountService, InstrumentService instrumentService, HoldingService holdingService, ApplicationEventPublisher eventPublisher, MarketSessionService marketSessionService) {
        this.tradeMapper = tradeMapper;
        this.accountService = accountService;
        this.instrumentService = instrumentService;
        this.holdingService = holdingService;
        this.eventPublisher = eventPublisher;
        this.marketSessionService = marketSessionService;
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
        if(!"BUY".equals(trade.getSide()) && !"SELL".equals(trade.getSide())){
            throw new ObjectInvalidException("Side must be BUY or SELL");
        }
        if(trade.getQuantity() == null || trade.getQuantity().compareTo(BigDecimal.ZERO) <= 0){
            throw new ObjectInvalidException("Quantity must be greater than 0");
        }

        Instrument instrument = instrumentService.getInstrumentById(trade.getInstrumentId());
        marketSessionService.assertMarketOpen(instrument);

        instrument = instrumentService.refreshPriceIfStale(instrument.getInstrumentId());
        assertPriceFresh(instrument);
        BigDecimal executionPrice = instrument.getCurrentPrice();
        if(executionPrice == null){
            throw new ObjectInvalidException("Unable to make trade at the moment; quote unavailable");
        }

        BigDecimal tradeValue = executionPrice.multiply(trade.getQuantity());
        Account account = accountService.getAccount(trade.getAccountId());
        if(!"ACTIVE".equals(account.getStatus())){
            throw new ObjectInvalidException("Account is not active: " + trade.getAccountId());
        }

        if("BUY".equals(trade.getSide())
                && (account.getBalance() == null || account.getBalance().compareTo(tradeValue) < 0)){
            throw new ObjectInvalidException("Insufficient balance for trade");
        }

        Holding holding = holdingService.findHolding(
                trade.getAccountId(),
                instrument.getInstrumentId());
        if("SELL".equals(trade.getSide())){
            if(holding == null){
                throw new ObjectInvalidException("No holding exists for this instrument");
            }
            if(holding.getQuantity().compareTo(trade.getQuantity()) < 0){
                throw new ObjectInvalidException("Insufficient quantity to sell");
            }
        }

        // Persist the execution price only after market, quote, account, and holding validation.
        trade.setExecutionPrice(executionPrice);
        if(trade.getExecutedAt() == null){
            trade.setExecutedAt(OffsetDateTime.now());
        }
        trade.setTradeValue(tradeValue);
        int tradeCreatedFlag = tradeMapper.createTrade(trade);
        if(tradeCreatedFlag < 1){
            throw new ObjectNotProcessedException("Could not create trade");
        }
    
        if(trade.getSide().equals("BUY")){

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
                holdingService.incrementHoldingQuantityInternal(holding.getAccountId(),holding.getInstrumentId(), trade.getQuantity());
            }
        }else if(trade.getSide().equals("SELL")){
            holdingService.decrementHoldingQuantityInternal(holding.getAccountId(),holding.getInstrumentId(), trade.getQuantity());
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