package com.neueda.leap.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.client.MarketDataClient;
import com.neueda.leap.dto.response.HistoryResponse;
import com.neueda.leap.dto.response.PriceBar;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.MarketDataUnavailableException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class MarketDataService implements IService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    private static final Pattern TICKER_PATTERN = Pattern.compile("^[A-Z.\\-]{1,10}$");
    private static final String FETCH_PERIOD = "5y";
    private static final String NOT_FOUND_MARKER = "NOT_FOUND";
    private static final Duration HISTORY_TTL = Duration.ofHours(6);
    private static final Duration NOT_FOUND_TTL = Duration.ofMinutes(10);
    private static final String SUBSCRIPTION_SET_KEY = "market:subscriptions";
    private static final String SUBSCRIPTION_COUNT_PREFIX = "market:subscription:count:";
    private static final String SUBSCRIPTION_HOT_UNTIL_PREFIX = "market:subscription:hot-until:";
    private static final String SUBSCRIPTION_LAST_REFRESH_PREFIX = "market:subscription:last-refresh:";
    private static final String BATCH_LOCK_KEY = "market:subscription:batch-lock";
    private static final String BATCH_LAST_CALL_KEY = "market:subscription:last-upstream-call-ms";

    private final long lockTtlSeconds;
    private final long refreshCooldownSeconds;
    private final long fetchRetryDelayMs;
    private final long hotRefreshIntervalSeconds;
    private final long warmRefreshIntervalSeconds;
    private final long coldRefreshIntervalSeconds;
    private final long hotWindowSeconds;
    private final long upstreamMinIntervalMillis;
    private final int batchMaxTickers;


    private final MarketDataClient client;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public MarketDataService(
            MarketDataClient client, 
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${market.lock-ttl-seconds:20}") long lockTtlSeconds,
            @Value("${market.refresh-cooldown-seconds:180}") long refreshCooldownSeconds,
            @Value("${market.fetch-retry-delay-ms:120}") long fetchRetryDelayMs,
            @Value("${market.subscription.hot-refresh-seconds:15}") long hotRefreshIntervalSeconds,
            @Value("${market.subscription.warm-refresh-seconds:45}") long warmRefreshIntervalSeconds,
            @Value("${market.subscription.cold-refresh-seconds:180}") long coldRefreshIntervalSeconds,
            @Value("${market.subscription.hot-window-seconds:120}") long hotWindowSeconds,
            @Value("${market.subscription.upstream-min-interval-ms:4000}") long upstreamMinIntervalMillis,
            @Value("${market.subscription.batch-max-tickers:25}") int batchMaxTickers) {
        this.client = client;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.lockTtlSeconds = lockTtlSeconds;
        this.refreshCooldownSeconds = refreshCooldownSeconds;
        this.fetchRetryDelayMs = fetchRetryDelayMs;
        this.hotRefreshIntervalSeconds = hotRefreshIntervalSeconds;
        this.warmRefreshIntervalSeconds = warmRefreshIntervalSeconds;
        this.coldRefreshIntervalSeconds = coldRefreshIntervalSeconds;
        this.hotWindowSeconds = hotWindowSeconds;
        this.upstreamMinIntervalMillis = upstreamMinIntervalMillis;
        this.batchMaxTickers = batchMaxTickers;
    }

    public HistoryResponse getHistory(String rawTicker, String period) {
        String ticker = normalizeTicker(rawTicker);
        HistoryResponse full = loadFullHistory(ticker);
        return slice(full, period);
    }

    public Map<String, Long> getSubscriptions() {
        Set<String> tickers = redis.opsForSet().members(SUBSCRIPTION_SET_KEY);
        Map<String, Long> subscriptions = new HashMap<>();
        if (tickers == null) {
            return subscriptions;
        }

        for (String ticker : tickers) {
            long count = readLong(SUBSCRIPTION_COUNT_PREFIX + ticker, 0L);
            if (count <= 0) {
                redis.opsForSet().remove(SUBSCRIPTION_SET_KEY, ticker);
                redis.delete(SUBSCRIPTION_COUNT_PREFIX + ticker);
                redis.delete(SUBSCRIPTION_HOT_UNTIL_PREFIX + ticker);
                redis.delete(SUBSCRIPTION_LAST_REFRESH_PREFIX + ticker);
                continue;
            }
            subscriptions.put(ticker, count);
        }
        return subscriptions;
    }

    public long subscribeTicker(String rawTicker) {
        String ticker = normalizeTicker(rawTicker);
        String countKey = SUBSCRIPTION_COUNT_PREFIX + ticker;
        long count = redis.opsForValue().increment(countKey);
        redis.opsForSet().add(SUBSCRIPTION_SET_KEY, ticker);
        long hotUntil = Instant.now().plusSeconds(hotWindowSeconds).toEpochMilli();
        redis.opsForValue().set(SUBSCRIPTION_HOT_UNTIL_PREFIX + ticker, Long.toString(hotUntil));
        return count;
    }

    public long unsubscribeTicker(String rawTicker) {
        String ticker = normalizeTicker(rawTicker);
        String countKey = SUBSCRIPTION_COUNT_PREFIX + ticker;
        Long value = redis.opsForValue().decrement(countKey);
        long count = value == null ? 0L : Math.max(0L, value);
        if (count == 0L) {
            redis.opsForSet().remove(SUBSCRIPTION_SET_KEY, ticker);
            redis.delete(countKey);
            redis.delete(SUBSCRIPTION_HOT_UNTIL_PREFIX + ticker);
            redis.delete(SUBSCRIPTION_LAST_REFRESH_PREFIX + ticker);
        }
        return count;
    }

    @Scheduled(fixedDelayString = "${market.subscription.scheduler-delay-ms:1000}")
    public void refreshSubscribedTickers() {
        Boolean lock = redis.opsForValue().setIfAbsent(BATCH_LOCK_KEY, "1", Duration.ofSeconds(3));
        if (!Boolean.TRUE.equals(lock)) {
            return;
        }

        try {
            long nowMillis = Instant.now().toEpochMilli();
            long lastCallMillis = readLong(BATCH_LAST_CALL_KEY, 0L);
            if (nowMillis - lastCallMillis < upstreamMinIntervalMillis) {
                return;
            }

            Set<String> tickers = redis.opsForSet().members(SUBSCRIPTION_SET_KEY);
            if (tickers == null || tickers.isEmpty()) {
                return;
            }

            List<String> due = new ArrayList<>();
            for (String ticker : tickers) {
                long count = readLong(SUBSCRIPTION_COUNT_PREFIX + ticker, 0L);
                if (count <= 0) {
                    continue;
                }
                if (isTickerDue(ticker, nowMillis)) {
                    due.add(ticker);
                }
            }

            if (due.isEmpty()) {
                return;
            }

            int upperBound = Math.min(batchMaxTickers, due.size());
            Set<String> batch = Set.copyOf(due.subList(0, upperBound));
            Map<String, HistoryResponse> histories = client.fetchHistoryBatch(batch, FETCH_PERIOD);
            for (String ticker : batch) {
                HistoryResponse history = histories.get(ticker);
                if (history == null || history.bars() == null || history.bars().isEmpty()) {
                    writeCache("history:" + ticker, NOT_FOUND_MARKER, NOT_FOUND_TTL);
                    continue;
                }
                writeJson("history:" + ticker, history);
                redis.opsForValue().set(
                        SUBSCRIPTION_LAST_REFRESH_PREFIX + ticker,
                        Long.toString(nowMillis)
                );
            }
            redis.opsForValue().set(BATCH_LAST_CALL_KEY, Long.toString(nowMillis));
        } catch (RuntimeException e) {
            log.warn("Failed to refresh subscribed tickers", e);
        } finally {
            redis.delete(BATCH_LOCK_KEY);
        }
    }

    private String normalizeTicker(String rawTicker) {
        String ticker = rawTicker == null ? "" : rawTicker.trim().toUpperCase();
        if (!TICKER_PATTERN.matcher(ticker).matches()) {
            throw new ObjectInvalidException("Invalid ticker: " + rawTicker);
        }
        return ticker;
    }

    private boolean isTickerDue(String ticker, long nowMillis) {
        long hotUntilMillis = readLong(SUBSCRIPTION_HOT_UNTIL_PREFIX + ticker, 0L);
        long intervalSeconds;
        if (hotUntilMillis > nowMillis) {
            intervalSeconds = hotRefreshIntervalSeconds;
        } else {
            long count = readLong(SUBSCRIPTION_COUNT_PREFIX + ticker, 0L);
            intervalSeconds = count > 0 ? warmRefreshIntervalSeconds : coldRefreshIntervalSeconds;
        }

        long lastRefreshMillis = readLong(SUBSCRIPTION_LAST_REFRESH_PREFIX + ticker, 0L);
        return nowMillis - lastRefreshMillis >= Duration.ofSeconds(intervalSeconds).toMillis();
    }

    private long readLong(String key, long defaultValue) {
        String value = readCache(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid numeric cache value for key {}: {}", key, value);
            return defaultValue;
        }
    }

    private HistoryResponse loadFullHistory(String ticker) {
        String cacheKey = "history:" + ticker;
        String lockKey = "lock:history:" + ticker;
        String cooldownKey = "cooldown:history:" + ticker;


        String cached = readCache(cacheKey);
        if (cached != null) {
            if (NOT_FOUND_MARKER.equals(cached)) {
                throw new ObjectNotFoundException("No market data for " + ticker);
            }
            try {
                return objectMapper.readValue(cached, HistoryResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("Discarding unreadable cache entry {}", cacheKey, e);
            }
        }

        if(Boolean.TRUE.equals(redis.hasKey(cooldownKey))){
            throw new MarketDataUnavailableException("Quote refresh in cooldown for " + ticker);
        }

        Boolean lockAcquired = redis.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(lockTtlSeconds));
        if (!Boolean.TRUE.equals(lockAcquired)) {
            try {
                Thread.sleep(fetchRetryDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            String retryCache = readCache(cacheKey);
            if (retryCache != null && !NOT_FOUND_MARKER.equals(retryCache)) {
                try {
                    return objectMapper.readValue(retryCache, HistoryResponse.class);
                } catch (JsonProcessingException e) {
                    log.warn("Unreadable retry cache for {}", cacheKey, e);
                }
            }

            throw new MarketDataUnavailableException("Fetch already in progress for " + ticker);
        }
        try {
            HistoryResponse fresh = client.fetchHistory(ticker, FETCH_PERIOD);
            validateHistory(fresh, ticker);
            writeJson(cacheKey, fresh);
            writeCache(cooldownKey, "1", Duration.ofSeconds(refreshCooldownSeconds));
            return fresh;
        } catch (ObjectNotFoundException e) {
            writeCache(cacheKey, NOT_FOUND_MARKER, NOT_FOUND_TTL);   // short-lived, so typos don't hit Yahoo repeatedly
            throw e;
        }finally{
            try{redis.delete(lockKey);} catch(Exception e){log.warn("Redis delete failed{}", lockKey, e);}
        }
    // MarketDataUnavailableException propagates uncached, so an outage is never stored
    }

    private void validateHistory(HistoryResponse response, String ticker) {
        if (response.bars() == null || response.bars().isEmpty()) {
            throw new MarketDataUnavailableException("Market data service returned no price bars for " + ticker);
        }
    }

        // MarketDataUnavailableException propagates uncached, so an outage is never stored
    private HistoryResponse slice(HistoryResponse full, String period) {
        List<PriceBar> bars = full.bars();
        if (bars.isEmpty()) {
            throw new MarketDataUnavailableException("No price bars available for " + full.ticker());
        }
        LocalDate anchor = bars.get(bars.size() - 1).date();   // latest bar, not today's date
        LocalDate cutoff = cutoffFor(period, anchor);
        List<PriceBar> sliced = cutoff == null
                ? bars
                : bars.stream().filter(b -> !b.date().isBefore(cutoff)).toList();

        if (sliced.isEmpty()) {
            throw new ObjectInvalidException("No price data available for period: " + period);
        }
        return new HistoryResponse(full.ticker(), period, sliced);
    }

    private LocalDate cutoffFor(String period, LocalDate anchor) {
        return switch (period.trim().toLowerCase()) {
            case "1m", "1mo" -> anchor.minusMonths(1);
            case "3m", "3mo" -> anchor.minusMonths(3);
            case "6m", "6mo" -> anchor.minusMonths(6);
            case "1y" -> anchor.minusYears(1);
            case "2y" -> anchor.minusYears(2);
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