package com.neueda.leap.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.neueda.leap.client.MarketDataClient;
import com.neueda.leap.dto.response.InstrumentResponse;
import com.neueda.leap.dto.response.MarketInstrumentMetadata;
import com.neueda.leap.dto.response.MarketQuote;
import com.neueda.leap.dto.response.QuoteStatus;

import com.neueda.leap.dto.response.PriceBar;

import com.neueda.leap.exception.ObjectAlreadyExistsException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.mapper.ExchangeMapper;
import com.neueda.leap.mapper.InstrumentMapper;
import com.neueda.leap.model.Exchange;
import com.neueda.leap.model.Instrument;

import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class InstrumentService implements IService {

    private static final Pattern TICKER_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9.\\-_=]{0,31}$");

    private final InstrumentMapper instrumentMapper;
    private final MarketDataClient marketDataClient;
    private final ExchangeMapper exchangeMapper;
    private final MarketDataService marketDataService;
    private final StringRedisTemplate redis;
    private final long quoteRefreshThresholdSeconds;
    private final long quoteLockTtlSeconds;
    private final long maxPriceAgeSeconds;

    public InstrumentService(
        InstrumentMapper instrumentMapper,
        MarketDataClient marketDataClient,
        ExchangeMapper exchangeMapper,
        StringRedisTemplate redis,
        MarketDataService marketDataService,
        @Value("${quote.refresh-threshold-seconds:120}") long quoteRefreshThresholdSeconds,
        @Value("${quote.lock-ttl-seconds:20}") long quoteLockTtlSeconds,
        @Value("${trade.max-price-age-seconds:180}") long maxPriceAgeSeconds) {
        this.instrumentMapper = instrumentMapper;
        this.marketDataClient = marketDataClient;
        this.exchangeMapper = exchangeMapper;
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
        if (ticker == null || ticker.isBlank()) {
            throw new ObjectNotFoundException("Ticker cannot be empty");
        }
        String normalizedTicker = normalizeTicker(ticker);
        Instrument instrument = instrumentMapper.findByTicker(normalizedTicker);
        if(instrument == null){
            throw new ObjectNotFoundException("Instrument not found: " + normalizedTicker);
        }
        return instrument;
    }

    @Transactional
    public Instrument resolveForTrading(String rawTicker) {
        String ticker = normalizeTicker(rawTicker);
        Instrument existing = instrumentMapper.findByTicker(ticker);
        if (existing != null) {
            return existing;
        }

        MarketInstrumentMetadata metadata = marketDataClient.lookupInstrument(ticker);
        validateMetadata(metadata, ticker);

        Exchange exchange = new Exchange(
                metadata.exchangeId(),
                metadata.exchangeName(),
                metadata.region(),
                metadata.timezone(),
                metadata.currency());
        exchangeMapper.insertIfAbsent(exchange);

        // The lookup price is a convenience for display: the market service falls back to the
        // last daily close when regularMarketPrice is absent, so it can be days old over a
        // weekend. Stamping it now() would present it as a live observation and let the first
        // trade execute against it, so it is backdated and only a real quote refresh sets a
        // current updated_at.
        instrumentMapper.insertIfAbsent(
                UUID.randomUUID(),
                ticker,
                metadata.name(),
                metadata.exchangeId(),
                metadata.currency(),
                metadata.priceScale(),
                metadata.currentPrice(),
                Instant.EPOCH.atOffset(ZoneOffset.UTC));

        Instrument resolved = instrumentMapper.findByTicker(ticker);
        if (resolved == null) {
            throw new ObjectNotProcessedException(
                    "Instrument could not be resolved: " + ticker);
        }
        return resolved;
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
                        i.getExchange(),
                        i.getCurrency()))
                .toList();
    }

    public Instrument refreshPriceIfStale(UUID instrumentId) {
        Instrument instrument = getInstrumentById(instrumentId);

        Optional<MarketQuote> latestQuote = marketDataService.getLatestQuote(instrument.getTicker());

        boolean cachedQuoteIsStale = latestQuote.isPresent()
                && !QuoteStatus.LIVE.equals(latestQuote.get().status());
        if (latestQuote.isPresent()
                && QuoteStatus.LIVE.equals(latestQuote.get().status())
                && isFresh(latestQuote.get().quoteTime())

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
            if (!cachedQuoteIsStale && ageSeconds <= quoteRefreshThresholdSeconds) {
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
            MarketQuote liveQuote = marketDataService.fetchLiveQuote(instrument.getTicker());
            if (liveQuote == null
                    || !QuoteStatus.LIVE.equals(liveQuote.status())
                    || !isFresh(liveQuote.quoteTime())
                    || !isNewerThanInstrument(liveQuote, instrument)) {
                throw new ObjectInvalidException(
                        "Unable to make trade at the moment; live quote unavailable");
            }


            instrument.setCurrentPrice(liveQuote.price());
            instrument.setUpdatedAt(
                    liveQuote.quoteTime().atOffset(ZoneOffset.UTC));


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

    private String normalizeTicker(String rawTicker) {
        String ticker = rawTicker == null ? "" : rawTicker.trim().toUpperCase();
        if (!TICKER_PATTERN.matcher(ticker).matches()) {
            throw new ObjectInvalidException("Invalid ticker: " + rawTicker);
        }
        return ticker;
    }

    private void validateMetadata(MarketInstrumentMetadata metadata, String ticker) {
        if (metadata == null
                || isBlank(metadata.name())
                || isBlank(metadata.exchangeId())
                || isBlank(metadata.exchangeName())
                || isBlank(metadata.region())
                || isBlank(metadata.timezone())
                || isBlank(metadata.currency())
                || metadata.priceScale() == null
                || metadata.priceScale().signum() <= 0
                || metadata.currentPrice() == null
                || metadata.currentPrice().signum() <= 0) {
            throw new ObjectInvalidException(
                    "Market metadata is incomplete for " + ticker);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}