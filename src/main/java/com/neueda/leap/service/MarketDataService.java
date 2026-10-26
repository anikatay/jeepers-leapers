package com.neueda.leap.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.client.MarketDataClient;
import com.neueda.leap.dto.response.FxRateResponse;
import com.neueda.leap.dto.response.HistoryResponse;
import com.neueda.leap.dto.response.IntradayHistoryResponse;
import com.neueda.leap.dto.response.MarketHeatmapResponse;
import com.neueda.leap.dto.response.MarketHeatmapSector;
import com.neueda.leap.dto.response.MarketHeatmapTicker;
import com.neueda.leap.dto.response.PriceBar;
import com.neueda.leap.dto.response.MarketQuote;
import com.neueda.leap.dto.response.MarketSector;
import com.neueda.leap.dto.response.MarketSectorCatalog;
import com.neueda.leap.dto.response.NewsResponse;
import com.neueda.leap.dto.response.MarketSectorTicker;
import com.neueda.leap.dto.response.QuoteStatus;
import com.neueda.leap.websocket.MarketQuoteWebSocketHandler;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.MarketDataUnavailableException;
import com.neueda.leap.exception.MarketClosedException;
import com.neueda.leap.mapper.InstrumentMapper;
import com.neueda.leap.model.Instrument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class MarketDataService implements IService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    private static final Pattern TICKER_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9.\\-_=]{0,31}$");
    private static final String FETCH_PERIOD = "5y";
    private static final String NOT_FOUND_MARKER = "NOT_FOUND";
    private static final Duration HISTORY_TTL = Duration.ofHours(6);

    private static final Duration INTRADAY_HISTORY_TTL = Duration.ofSeconds(60);
    private static final String INTRADAY_HISTORY_PREFIX = "history:intraday:";

    /** Headlines move on the order of minutes and every client asks for the same few tickers. */
    private static final Duration NEWS_TTL = Duration.ofMinutes(5);
    private static final String NEWS_PREFIX = "news:";
    private static final Duration NOT_FOUND_TTL = Duration.ofMinutes(10);
    private static final int HISTORY_BATCH_SIZE = 50;
    private static final String BATCH_HISTORY_PREFIX = "history:batch:";
    private static final String SUBSCRIPTION_SET_KEY = "market:subscriptions";
    private static final String SUBSCRIPTION_COUNT_PREFIX = "market:subscription:count:";
    private static final String SUBSCRIPTION_HOT_UNTIL_PREFIX = "market:subscription:hot-until:";
    private static final String SUBSCRIPTION_LAST_REFRESH_PREFIX = "market:subscription:last-refresh:";
    private static final String LATEST_QUOTE_PREFIX = "quote:latest:";
    private static final String LAST_KNOWN_QUOTE_PREFIX = "quote:last-known:";
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "EUR", "INR", "GBP");

    private final long lockTtlSeconds;
    private final long refreshCooldownSeconds;
    private final long fetchRetryDelayMs;
    private final long hotWindowSeconds;
    private final long latestQuoteTtlSeconds;
    private final long lastKnownQuoteTtlSeconds;
    private final long fxRateTtlSeconds;

    private final MarketDataClient client;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<MarketQuoteWebSocketHandler> quoteWebSocketHandler;
    private final InstrumentMapper instrumentMapper;
    private final MarketSessionService marketSessionService;

    public MarketDataService(
            MarketDataClient client, 
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            ObjectProvider<MarketQuoteWebSocketHandler> quoteWebSocketHandler,
            InstrumentMapper instrumentMapper,
            MarketSessionService marketSessionService,
            @Value("${market.lock-ttl-seconds:20}") long lockTtlSeconds,
            @Value("${market.refresh-cooldown-seconds:180}") long refreshCooldownSeconds,
            @Value("${market.fetch-retry-delay-ms:120}") long fetchRetryDelayMs,
            @Value("${market.subscription.hot-window-seconds:120}") long hotWindowSeconds,
            @Value("${quote.cache-ttl-seconds:60}") long latestQuoteTtlSeconds,
            @Value("${quote.last-known-ttl-seconds:604800}") long lastKnownQuoteTtlSeconds,
            @Value("${fx.rate-cache-ttl-seconds:60}") long fxRateTtlSeconds) {
        this.client = client;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.quoteWebSocketHandler = quoteWebSocketHandler;
        this.instrumentMapper = instrumentMapper;
        this.marketSessionService = marketSessionService;
        this.lockTtlSeconds = lockTtlSeconds;
        this.refreshCooldownSeconds = refreshCooldownSeconds;
        this.fetchRetryDelayMs = fetchRetryDelayMs;
        this.hotWindowSeconds = hotWindowSeconds;
        this.latestQuoteTtlSeconds = latestQuoteTtlSeconds;
        this.lastKnownQuoteTtlSeconds = lastKnownQuoteTtlSeconds;
        this.fxRateTtlSeconds = fxRateTtlSeconds;
    }

    public String normalizeForSubscription(String rawTicker) {
        return normalizeTicker(rawTicker);
    }

    public HistoryResponse getHistory(String rawTicker, String period) {
        String ticker = normalizeTicker(rawTicker);
        HistoryResponse full = loadFullHistory(ticker);
        return slice(full, period);
    }

    public Map<String, HistoryResponse> getHistoryBatch(String rawTickers, String period) {
        String fetchPeriod = batchFetchPeriod(period);
        Set<String> tickers = normalizeTickers(rawTickers);
        Map<String, HistoryResponse> result = new LinkedHashMap<>();
        Set<String> missing = new LinkedHashSet<>();

        for (String ticker : tickers) {
            String cacheKey = batchHistoryCacheKey(ticker, fetchPeriod);
            String cached = readCache(cacheKey);
            if (NOT_FOUND_MARKER.equals(cached)) {
                result.put(ticker, emptyHistory(ticker, period));
                continue;
            }
            if (cached != null) {
                try {
                    HistoryResponse full = objectMapper.readValue(cached, HistoryResponse.class);
                    if (full.bars() != null && !full.bars().isEmpty()) {
                        result.put(ticker, slice(full, period));
                        continue;
                    }
                    log.warn("Cached history has no bars for {}", ticker);
                } catch (JsonProcessingException e) {
                    log.warn("Discarding unreadable cache entry {}", cacheKey, e);
                }
            }
            missing.add(ticker);
        }

        List<String> pending = new ArrayList<>(missing);
        for (int start = 0; start < pending.size(); start += HISTORY_BATCH_SIZE) {
            int end = Math.min(start + HISTORY_BATCH_SIZE, pending.size());
            Set<String> batch = new LinkedHashSet<>(pending.subList(start, end));
            Map<String, HistoryResponse> fresh = client.fetchHistoryBatch(batch, fetchPeriod);

            for (String ticker : batch) {
                HistoryResponse full = fresh.get(ticker);
                if (full == null || full.bars() == null || full.bars().isEmpty()) {
                    log.warn("No batch history returned for {}", ticker);
                    writeCache(
                            batchHistoryCacheKey(ticker, fetchPeriod),
                            NOT_FOUND_MARKER,
                            NOT_FOUND_TTL);
                    result.put(ticker, emptyHistory(ticker, period));
                    continue;
                }

                validateHistory(full, ticker);
                String cacheKey = batchHistoryCacheKey(ticker, fetchPeriod);
                writeJson(cacheKey, full);
                writeCache(
                        "cooldown:" + cacheKey,
                        "1",
                        Duration.ofSeconds(refreshCooldownSeconds));
                result.put(ticker, slice(full, period));
            }
        }
        return result;
    }
    public IntradayHistoryResponse getIntradayHistory(String rawTicker) {
        String ticker = normalizeTicker(rawTicker);
        String cacheKey = INTRADAY_HISTORY_PREFIX + ticker;

        String cached = readCache(cacheKey);
        if (NOT_FOUND_MARKER.equals(cached)) {
            throw new ObjectNotFoundException("No intraday market data for " + ticker);
        }
        if (cached != null) {
            try {
                IntradayHistoryResponse parsed =
                        objectMapper.readValue(cached, IntradayHistoryResponse.class);
                if (parsed.bars() != null && !parsed.bars().isEmpty()) {
                    return parsed;
                }
                log.warn("Cached intraday history has no bars for {}", ticker);
            } catch (JsonProcessingException e) {
                log.warn("Discarding unreadable cache entry {}", cacheKey, e);
            }
        }

        try {
            IntradayHistoryResponse fresh = client.fetchIntradayHistory(ticker);
            try {
                writeCache(cacheKey, objectMapper.writeValueAsString(fresh), INTRADAY_HISTORY_TTL);
            } catch (JsonProcessingException e) {
                log.warn("Could not serialize intraday history for {}", ticker, e);
            }
            return fresh;
        } catch (ObjectNotFoundException e) {
            writeCache(cacheKey, NOT_FOUND_MARKER, NOT_FOUND_TTL);
            throw e;
        }
    }

    /**
     * Headlines for the given tickers, newest first. The whole response is cached under the
     * normalised ticker set, so the usual case — every client on the home page asking about
     * the same holdings — costs one upstream call per TTL rather than one per visitor.
     */
    public NewsResponse getNews(String rawTickers, int limit) {
        Set<String> tickers = normalizeTickers(rawTickers);
        int safeLimit = Math.max(1, Math.min(limit, 20));
        String cacheKey = NEWS_PREFIX + safeLimit + ":" + String.join(",", tickers);

        String cached = readCache(cacheKey);
        if (cached != null) {
            try {
                NewsResponse parsed = objectMapper.readValue(cached, NewsResponse.class);
                if (parsed.items() != null) {
                    return parsed;
                }
            } catch (JsonProcessingException e) {
                log.warn("Discarding unreadable cache entry {}", cacheKey, e);
            }
        }

        NewsResponse fresh = client.fetchNews(tickers, safeLimit);
        try {
            writeCache(cacheKey, objectMapper.writeValueAsString(fresh), NEWS_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize news for {}", cacheKey, e);
        }
        return fresh;
    }

    public Map<String, IntradayHistoryResponse> getIntradayHistoryBatch(String rawTickers) {
        Set<String> tickers = normalizeTickers(rawTickers);
        Map<String, IntradayHistoryResponse> result = new LinkedHashMap<>();

        for (String ticker : tickers) {
            try {
                result.put(ticker, getIntradayHistory(ticker));
            } catch (RuntimeException e) {
                log.warn("No intraday history for {}", ticker, e);
                result.put(ticker, new IntradayHistoryResponse(ticker, "1d", List.of()));
            }
        }
        return result;
    }

    public MarketQuote cacheLatestQuote(MarketQuote quote) {
        if (quote == null || quote.ticker() == null || quote.price() == null
                || quote.quoteTime() == null) {
            throw new ObjectInvalidException("Invalid market quote");
        }

        String ticker = normalizeTicker(quote.ticker());
        MarketQuote normalized = new MarketQuote(
                quote.type(),
                ticker,
                quote.price(),
                quote.quoteTime(),
                quote.receivedTime() == null ? Instant.now() : quote.receivedTime(),
                quote.source(),
                QuoteStatus.LIVE);
        try {
            String payload = objectMapper.writeValueAsString(normalized);
            redis.opsForValue().set(
                    LATEST_QUOTE_PREFIX + ticker,
                    payload,
                    Duration.ofSeconds(latestQuoteTtlSeconds));
            redis.opsForValue().set(
                    LAST_KNOWN_QUOTE_PREFIX + ticker,
                    payload,
                    Duration.ofSeconds(lastKnownQuoteTtlSeconds));
        } catch (JsonProcessingException e) {
            throw new ObjectInvalidException("Unable to cache market quote for " + ticker, e);
        }
        return normalized;
    }

    public Optional<MarketQuote> getLatestQuote(String rawTicker) {
        String ticker = normalizeTicker(rawTicker);
        return readCachedQuote(ticker)
                .map(cached -> withStatus(
                        cached.quote(),
                        cached.live() ? QuoteStatus.LIVE : QuoteStatus.STALE));
    }

    public MarketQuote fetchLiveQuote(String rawTicker) {
        String ticker = normalizeTicker(rawTicker);
        return cacheLatestQuote(client.fetchLatestQuote(ticker));
    }

    public MarketQuote getQuoteSnapshot(String rawTicker) {
        String ticker = normalizeTicker(rawTicker);
        CachedQuote cached = readCachedQuote(ticker)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "No cached market quote for " + ticker));
        return withStatus(
                cached.quote(),
                resolveQuoteStatus(ticker, cached.live()));
    }

    /**
     * The cached quote, falling back to a live fetch when nothing is cached.
     *
     * <p>The quote cache is fed by the stream, and the stream only carries the sidecar's
     * configured sector universe plus whatever a page has transiently subscribed. Everything
     * outside that — crypto, LSE, NSE, and the rest of the seeded instruments — never gets a
     * cached quote, so a snapshot read would fail for it indefinitely even though the price is
     * one call away. This is the same cache-then-fetch order the trade path already uses in
     * InstrumentService#refreshPriceIfStale.
     *
     * <p>Deliberately separate from {@link #getQuoteSnapshot}: the heatmap resolves ~110 tickers
     * in a loop and relies on the not-found signal to render an UNAVAILABLE tile, so it must not
     * pay for a fetch per empty tile.
     */
    public MarketQuote getQuoteOrFetch(String rawTicker) {
        String ticker = normalizeTicker(rawTicker);
        return readCachedQuote(ticker)
                .map(cached -> withStatus(cached.quote(), resolveQuoteStatus(ticker, cached.live())))
                // A freshly fetched quote is live by definition, but it still goes through
                // resolveQuoteStatus so a closed session is reported as CLOSED rather than LIVE.
                .orElseGet(() -> withStatus(fetchLiveQuote(ticker), resolveQuoteStatus(ticker, true)));
    }

    public MarketSectorCatalog getSectorCatalog() {
        return client.fetchSectorCatalog();
    }

    public MarketSector getSector(String rawSectorId) {
        String sectorId = normalizeSectorId(rawSectorId);
        return getSectorCatalog().sectors().stream()
                .filter(sector -> sectorId.equals(sector.id()))
                .findFirst()
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Unknown market sector: " + rawSectorId));
    }

    public MarketHeatmapResponse getHeatmap(String rawSectorId) {
        MarketSectorCatalog catalog = getSectorCatalog();
        List<MarketSector> sectors = catalog.sectors();
        if (rawSectorId != null && !rawSectorId.isBlank()) {
            String sectorId = normalizeSectorId(rawSectorId);
            sectors = sectors.stream()
                    .filter(sector -> sectorId.equals(sector.id()))
                    .toList();
            if (sectors.isEmpty()) {
                throw new ObjectNotFoundException(
                        "Unknown market sector: " + rawSectorId);
            }
        }

        Instant asOf = Instant.now();
        List<MarketHeatmapSector> heatmapSectors = sectors.stream()
                .map(sector -> new MarketHeatmapSector(
                        sector.id(),
                        sector.name(),
                        sector.tickers().stream()
                                .map(ticker -> toHeatmapTicker(ticker))
                                .toList()))
                .toList();
        return new MarketHeatmapResponse(
                catalog.market(),
                catalog.selectionBasis(),
                catalog.universeVersion(),
                catalog.generatedAt(),
                asOf,
                heatmapSectors);
    }

    public BigDecimal getExchangeRate(String rawBaseCurrency, String rawQuoteCurrency) {
        String baseCurrency = normalizeCurrency(rawBaseCurrency);
        String quoteCurrency = normalizeCurrency(rawQuoteCurrency);
        if (baseCurrency.equals(quoteCurrency)) {
            return BigDecimal.ONE;
        }

        String cacheKey = "fx:rate:" + baseCurrency + ":" + quoteCurrency;
        String lockKey = "lock:" + cacheKey;
        String cached = readCache(cacheKey);
        if (cached != null) {
            try {
                BigDecimal rate = new BigDecimal(cached);
                if (rate.signum() > 0) {
                    return rate;
                }
            } catch (NumberFormatException e) {
                log.warn("Discarding invalid FX cache entry {}", cacheKey, e);
                redis.delete(cacheKey);
            }
        }

        Boolean lockAcquired = redis.opsForValue().setIfAbsent(
                lockKey,
                "1",
                Duration.ofSeconds(lockTtlSeconds));
        if (!Boolean.TRUE.equals(lockAcquired)) {
            try {
                Thread.sleep(fetchRetryDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String retryCache = readCache(cacheKey);
            if (retryCache != null) {
                try {
                    BigDecimal rate = new BigDecimal(retryCache);
                    if (rate.signum() > 0) {
                        return rate;
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid retry FX cache entry {}", cacheKey, e);
                }
            }
            throw new MarketDataUnavailableException(
                    "FX rate fetch already in progress for " + baseCurrency + "/" + quoteCurrency);
        }

        try {
            FxRateResponse response = client.fetchExchangeRate(baseCurrency, quoteCurrency);
            if (response.rate() == null || response.rate().signum() <= 0) {
                throw new MarketDataUnavailableException(
                        "Market service returned an invalid FX rate");
            }
            BigDecimal rate = response.rate().setScale(12, RoundingMode.HALF_UP);
            writeCache(cacheKey, rate.toPlainString(), Duration.ofSeconds(fxRateTtlSeconds));
            return rate;
        } finally {
            redis.delete(lockKey);
        }
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
        client.subscribeLiveTicker(ticker);
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
            client.unsubscribeLiveTicker(ticker);
            redis.opsForSet().remove(SUBSCRIPTION_SET_KEY, ticker);
            redis.delete(countKey);
            redis.delete(SUBSCRIPTION_HOT_UNTIL_PREFIX + ticker);
            redis.delete(SUBSCRIPTION_LAST_REFRESH_PREFIX + ticker);
        }
        return count;
    }

    private String normalizeTicker(String rawTicker) {
        String ticker = rawTicker == null ? "" : rawTicker.trim().toUpperCase();
        if (!TICKER_PATTERN.matcher(ticker).matches()) {
            throw new ObjectInvalidException("Invalid ticker: " + rawTicker);
        }
        return ticker;
    }

    private Set<String> normalizeTickers(String rawTickers) {
        if (rawTickers == null || rawTickers.isBlank()) {
            throw new ObjectInvalidException("Tickers cannot be empty");
        }

        Set<String> tickers = new LinkedHashSet<>();
        for (String rawTicker : rawTickers.split(",")) {
            if (!rawTicker.isBlank()) {
                tickers.add(normalizeTicker(rawTicker));
            }
        }
        if (tickers.isEmpty()) {
            throw new ObjectInvalidException("Tickers cannot be empty");
        }
        return tickers;
    }

    private String batchFetchPeriod(String rawPeriod) {
        if (rawPeriod == null) {
            throw new ObjectInvalidException("Unsupported period: null");
        }
        return switch (rawPeriod.trim().toLowerCase()) {
            case "1m", "1mo" -> "1mo";
            case "3m", "3mo" -> "3mo";
            case "6m", "6mo" -> "6mo";
            case "1y" -> "1y";
            case "2y" -> "2y";
            case "all", "max", "5y" -> FETCH_PERIOD;
            default -> throw new ObjectInvalidException("Unsupported period: " + rawPeriod);
        };
    }

    private String batchHistoryCacheKey(String ticker, String fetchPeriod) {
        return BATCH_HISTORY_PREFIX + fetchPeriod + ":" + ticker;
    }

    private HistoryResponse emptyHistory(String ticker, String period) {
        return new HistoryResponse(ticker, period, List.of());
    }

    private String normalizeCurrency(String rawCurrency) {
        String currency = rawCurrency == null ? "" : rawCurrency.trim().toUpperCase();
        if (!CURRENCY_PATTERN.matcher(currency).matches()
                || !SUPPORTED_CURRENCIES.contains(currency)) {
            throw new ObjectInvalidException("Unsupported currency: " + rawCurrency);
        }
        return currency;
    }

    private String normalizeSectorId(String rawSectorId) {
        String sectorId = rawSectorId == null ? "" : rawSectorId.trim().toLowerCase();
        if (!sectorId.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$")) {
            throw new ObjectInvalidException("Invalid market sector: " + rawSectorId);
        }
        return sectorId;
    }

    private MarketHeatmapTicker toHeatmapTicker(MarketSectorTicker sectorTicker) {
        MarketQuote value;
        try {
            value = getQuoteSnapshot(sectorTicker.ticker());
        } catch (ObjectNotFoundException e) {
            return new MarketHeatmapTicker(
                    sectorTicker.ticker(),
                    sectorTicker.rank(),
                    null,
                    null,
                    null,
                    null,
                    QuoteStatus.UNAVAILABLE);
        }

        return new MarketHeatmapTicker(
                value.ticker(),
                sectorTicker.rank(),
                value.price(),
                value.quoteTime(),
                value.receivedTime(),
                value.source(),
                value.status());
    }

    private Optional<CachedQuote> readCachedQuote(String ticker) {
        String liveKey = LATEST_QUOTE_PREFIX + ticker;
        Optional<MarketQuote> liveQuote = parseCachedQuote(liveKey, readCache(liveKey));
        if (liveQuote.isPresent()) {
            return Optional.of(new CachedQuote(liveQuote.get(), true));
        }

        String lastKnownKey = LAST_KNOWN_QUOTE_PREFIX + ticker;
        Optional<MarketQuote> lastKnownQuote = parseCachedQuote(
                lastKnownKey,
                readCache(lastKnownKey));
        return lastKnownQuote.map(quote -> new CachedQuote(quote, false));
    }

    private Optional<MarketQuote> parseCachedQuote(String key, String cached) {
        if (cached == null) {
            return Optional.empty();
        }
        try {
            MarketQuote quote = objectMapper.readValue(cached, MarketQuote.class);
            return quote == null ? Optional.empty() : Optional.of(quote);
        } catch (JsonProcessingException e) {
            log.warn("Discarding unreadable quote cache entry {}", key, e);
            redis.delete(key);
            return Optional.empty();
        }
    }

    private QuoteStatus resolveQuoteStatus(String ticker, boolean live) {
        Instrument instrument;
        try {
            instrument = instrumentMapper.findByTicker(ticker);
        } catch (DataAccessException e) {
            log.warn("Could not resolve market session for quote {}", ticker, e);
            return live ? QuoteStatus.LIVE : QuoteStatus.STALE;
        }

        if (instrument == null) {
            return live ? QuoteStatus.LIVE : QuoteStatus.STALE;
        }

        try {
            marketSessionService.assertMarketOpen(instrument);
            return live ? QuoteStatus.LIVE : QuoteStatus.STALE;
        } catch (MarketClosedException e) {
            return QuoteStatus.CLOSED;
        } catch (ObjectNotFoundException | DataAccessException e) {
            log.warn("Could not determine market session for quote {}", ticker, e);
            return live ? QuoteStatus.LIVE : QuoteStatus.STALE;
        }
    }

    private MarketQuote withStatus(MarketQuote quote, QuoteStatus status) {
        return new MarketQuote(
                quote.type(),
                quote.ticker(),
                quote.price(),
                quote.quoteTime(),
                quote.receivedTime(),
                quote.source(),
                status);
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

    private record CachedQuote(MarketQuote quote, boolean live) {
    }
}