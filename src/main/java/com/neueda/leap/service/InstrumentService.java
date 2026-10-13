package com.neueda.leap.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.neueda.leap.dto.response.HistoryResponse;
import com.neueda.leap.dto.response.InstrumentResponse;
import com.neueda.leap.dto.response.MarketQuote;
import com.neueda.leap.dto.response.PriceBar;
import com.neueda.leap.exception.ObjectAlreadyExistsException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.mapper.InstrumentMapper;
import com.neueda.leap.model.Instrument;

import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class InstrumentService implements IService {

    private final InstrumentMapper instrumentMapper;
    private final MarketDataService marketDataService;
    private final StringRedisTemplate redis;
    private final long quoteRefreshThresholdSeconds;
    private final long quoteLockTtlSeconds;
    private final long maxPriceAgeSeconds;

    public InstrumentService(
        InstrumentMapper instrumentMapper,
        StringRedisTemplate redis,
        MarketDataService marketDataService,
        @Value("${quote.refresh-threshold-seconds:120}") long quoteRefreshThresholdSeconds,
        @Value("${quote.lock-ttl-seconds:20}") long quoteLockTtlSeconds,
        @Value("${trade.max-price-age-seconds:180}") long maxPriceAgeSeconds) {
        this.instrumentMapper = instrumentMapper;
        this.redis = redis;
        this.marketDataService = marketDataService;
        this.quoteRefreshThresholdSeconds = quoteRefreshThresholdSeconds;
        this.quoteLockTtlSeconds = quoteLockTtlSeconds;
        this.maxPriceAgeSeconds = maxPriceAgeSeconds;
    }

    public Instrument getInstrumentById(UUID instrumentId){
        if(instrumentId == null){
            throw new ObjectNotFoundException("Instrument ID cannot be null");
        }
        Instrument instrument = instrumentMapper.findById(instrumentId);
        if(instrument == null){
            throw new ObjectNotFoundException("Instrument not found: " + instrumentId);
        }
        return instrument;
    }

    public Instrument getInstrumentByTicker(String ticker) {
        if(ticker == null || ticker.isEmpty()) {
            throw new ObjectNotFoundException("Ticker cannot be empty");
        }
        Instrument instrument = instrumentMapper.findByTicker(ticker);
        if(instrument == null){
            throw new ObjectNotFoundException("Instrument not found ");
        }
        return instrument;
    }

    @Transactional
    public Instrument addInstrument(Instrument instrument) {
        if(instrument.getInstrumentId() == null){
            throw new ObjectNotFoundException("Instrument ID cannot be null");
        }
        if(instrumentMapper.findById(instrument.getInstrumentId()) != null){
            throw new ObjectAlreadyExistsException("Creating this instrument would lead to duplicate instruments");
        }
        if(instrument.getTicker() == null){
            throw new ObjectNotFoundException("Ticker cannot be empty");
        }
        if(instrumentMapper.findByTicker(instrument.getTicker()) != null){
            throw new ObjectAlreadyExistsException("Creating this instrument would lead to duplicate instruments");
        }
        if(instrument.getExchange() == null){
            throw new ObjectNotFoundException("Exchange ID cannot be null");
        }
        if(instrument.getCurrentPrice().compareTo(BigDecimal.ZERO) <= 0){
            throw new ObjectNotFoundException("Current price cannot be negeative");
        }
        
        
        if(instrument.getUpdatedAt() == null){
            instrument.setUpdatedAt(OffsetDateTime.now());
        }
        int instrumentAddedFlag = instrumentMapper.addInstrument(instrument);
        if(instrumentAddedFlag < 1){
            throw new ObjectNotProcessedException("Could Not add this instrument " + instrument);
        }
        return instrument;
    }

    @Transactional 
    public void updatePrice(UUID instrumentId, BigDecimal price){
        updatePrice(instrumentId, price, OffsetDateTime.now());
    }

    @Transactional
    public void updatePrice(UUID instrumentId, BigDecimal price, OffsetDateTime updatedAt){
        Instrument instrument = getInstrumentById(instrumentId);
        instrument.setCurrentPrice(price);
        instrument.setUpdatedAt(updatedAt);
        int instrumentUpdateFlag = instrumentMapper.updatePrice(instrument);
        if(instrumentUpdateFlag < 1){
            throw new ObjectNotProcessedException("Instrument was unable to be updated " + instrument);
        }
    }
    
    public List<InstrumentResponse> findAllInstruments(){
        List<Instrument> instruments = instrumentMapper.findAll();
        return instruments.stream()
                .map(i -> new InstrumentResponse(
                        i.getInstrumentId(),
                        i.getTicker(),
                        i.getName(),
                        i.getCurrentPrice(),
                        i.getExchange()))
                .toList();
    }

    public Instrument refreshPriceIfStale(UUID instrumentId) {
        Instrument instrument = getInstrumentById(instrumentId);

        Optional<MarketQuote> latestQuote = marketDataService.getLatestQuote(instrument.getTicker());
        if (latestQuote.isPresent() && isFresh(latestQuote.get().quoteTime())
                && isNewerThanInstrument(latestQuote.get(), instrument)) {
            MarketQuote quote = latestQuote.get();
            instrument.setCurrentPrice(quote.price());
            instrument.setUpdatedAt(quote.quoteTime().atOffset(ZoneOffset.UTC));
            if (instrumentMapper.updatePrice(instrument) < 1) {
                throw new ObjectNotProcessedException(
                        "Failed to persist live price for " + instrument.getTicker());
            }
            return instrument;
        }

        if (instrument.getUpdatedAt() != null) {
            long ageSeconds = Duration.between(instrument.getUpdatedAt(), OffsetDateTime.now()).getSeconds();
            if (ageSeconds <= quoteRefreshThresholdSeconds) {
                return instrument;
            }
        }

        String lockKey = "lock:quote:" + instrument.getTicker();
        Boolean acquired = redis.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(quoteLockTtlSeconds));

        if (!Boolean.TRUE.equals(acquired)) {
            // Another request is refreshing this ticker; return latest persisted snapshot.
            return getInstrumentById(instrumentId);
        }

        try {
            HistoryResponse history = marketDataService.getHistory(instrument.getTicker(), "1mo");
            if (history.bars() == null || history.bars().isEmpty()) {
                return instrument;
            }

            PriceBar latest = history.bars().get(history.bars().size() - 1);
            instrument.setCurrentPrice(latest.close());
            instrument.setUpdatedAt(latest.date().atStartOfDay().atOffset(ZoneOffset.UTC));

            int updated = instrumentMapper.updatePrice(instrument);
            if (updated < 1) {
                throw new ObjectNotProcessedException("Failed to refresh price for " + instrument.getTicker());
            }

            return instrument;
        } finally {
            redis.delete(lockKey);
        }
    }

    private boolean isFresh(java.time.Instant quoteTime) {
        Duration age = Duration.between(quoteTime, java.time.Instant.now());
        return !age.isNegative() && age.compareTo(Duration.ofSeconds(maxPriceAgeSeconds)) <= 0;
    }

    private boolean isNewerThanInstrument(MarketQuote quote, Instrument instrument) {
        return instrument.getUpdatedAt() == null
                || instrument.getUpdatedAt().isBefore(quote.quoteTime().atOffset(ZoneOffset.UTC));
    }

}