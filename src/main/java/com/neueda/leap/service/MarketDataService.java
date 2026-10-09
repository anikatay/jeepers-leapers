package com.neueda.leap.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.client.MarketDataClient;
import com.neueda.leap.dto.response.HistoryResponse;
import com.neueda.leap.dto.response.PriceBar;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class MarketDataService implements IService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    private static final Pattern TICKER_PATTERN = Pattern.compile("^[A-Z.\\-]{1,10}$");
    private static final String FETCH_PERIOD = "5y";
    private static final String NOT_FOUND_MARKER = "NOT_FOUND";
    private static final Duration HISTORY_TTL = Duration.ofHours(6);
    private static final Duration NOT_FOUND_TTL = Duration.ofMinutes(10);

    private final MarketDataClient client;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public MarketDataService(MarketDataClient client, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.client = client;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public HistoryResponse getHistory(String rawTicker, String period) {
        String ticker = normalizeTicker(rawTicker);
        HistoryResponse full = loadFullHistory(ticker);
        return slice(full, period);
    }

    private String normalizeTicker(String rawTicker) {
        String ticker = rawTicker == null ? "" : rawTicker.trim().toUpperCase();
        if (!TICKER_PATTERN.matcher(ticker).matches()) {
            throw new ObjectInvalidException("Invalid ticker: " + rawTicker);
        }
        return ticker;
    }

    private HistoryResponse loadFullHistory(String ticker) {
        String key = "history:" + ticker;

        String cached = readCache(key);
        if (cached != null) {
            if (NOT_FOUND_MARKER.equals(cached)) {
                throw new ObjectNotFoundException("No market data for " + ticker);
            }
            try {
                return objectMapper.readValue(cached, HistoryResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("Discarding unreadable cache entry {}", key, e);
            }
        }

        try {
            HistoryResponse fresh = client.fetchHistory(ticker, FETCH_PERIOD);
            writeJson(key, fresh);
            return fresh;
        } catch (ObjectNotFoundException e) {
            writeCache(key, NOT_FOUND_MARKER, NOT_FOUND_TTL);   // short-lived, so typos don't hit Yahoo repeatedly
            throw e;
        }
        // MarketDataUnavailableException propagates uncached, so an outage is never stored
    }

    private HistoryResponse slice(HistoryResponse full, String period) {
        List<PriceBar> bars = full.bars();
        if (bars.isEmpty()) {
            return new HistoryResponse(full.ticker(), period, bars);
        }
        LocalDate anchor = bars.get(bars.size() - 1).date();   // latest bar, not today's date
        LocalDate cutoff = cutoffFor(period, anchor);
        List<PriceBar> sliced = cutoff == null
                ? bars
                : bars.stream().filter(b -> !b.date().isBefore(cutoff)).toList();
        return new HistoryResponse(full.ticker(), period, sliced);
    }

    private LocalDate cutoffFor(String period, LocalDate anchor) {
        return switch (period.trim().toLowerCase()) {
            case "1m", "1mo" -> anchor.minusMonths(1);
            case "3m", "3mo" -> anchor.minusMonths(3);
            case "6m", "6mo" -> anchor.minusMonths(6);
            case "1y" -> anchor.minusYears(1);
            case "all", "max", "5y" -> null;
            default -> throw new ObjectInvalidException("Unsupported period: " + period);
        };
    }

    // Redis problems should degrade to "no cache," not break the endpoint
    private String readCache(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis read failed for {}", key, e);
            return null;
        }
    }

    private void writeCache(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("Redis write failed for {}", key, e);
        }
    }

    private void writeJson(String key, HistoryResponse value) {
        try {
            writeCache(key, objectMapper.writeValueAsString(value), HISTORY_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize history for {}", key, e);
        }
    }
}