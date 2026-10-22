package com.neueda.leap.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.client.MarketDataClient;
import com.neueda.leap.dto.response.HistoryResponse;
import com.neueda.leap.dto.response.MarketQuote;
import com.neueda.leap.dto.response.MarketHeatmapResponse;
import com.neueda.leap.dto.response.MarketSector;
import com.neueda.leap.dto.response.MarketSectorCatalog;
import com.neueda.leap.dto.response.MarketSectorTicker;
import com.neueda.leap.dto.response.PriceBar;
import com.neueda.leap.dto.response.QuoteStatus;
import com.neueda.leap.exception.MarketClosedException;
import com.neueda.leap.exception.MarketDataUnavailableException;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.mapper.InstrumentMapper;
import com.neueda.leap.model.Instrument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.beans.factory.ObjectProvider;
import com.neueda.leap.websocket.MarketQuoteWebSocketHandler;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataServiceTest {

    @Mock
    private MarketDataClient marketDataClient;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ObjectProvider<MarketQuoteWebSocketHandler> quoteWebSocketHandler;

    @Mock
    private InstrumentMapper instrumentMapper;

    @Mock
    private MarketSessionService marketSessionService;

    private MarketDataService marketDataService;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
        lenient().when(redis.hasKey(any())).thenReturn(false);
        lenient().when(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);
        marketDataService = new MarketDataService(
            marketDataClient,
            redis,
            objectMapper,
            quoteWebSocketHandler,
            instrumentMapper,
            marketSessionService,
            20,
            180,
            120,
            120,
            60,
            604800,
            60);
    }

    @Test
    void testCacheLatestQuoteWritesLiveAndLastKnownEntries() throws Exception {
        MarketQuote quote = new MarketQuote(
                "QUOTE",
                " aapl ",
                BigDecimal.valueOf(180),
                Instant.parse("2026-10-20T19:00:00Z"),
                Instant.parse("2026-10-20T19:00:01Z"),
                "test");
        when(objectMapper.writeValueAsString(any(MarketQuote.class))).thenReturn("{\"quote\":true}");

        MarketQuote normalized = marketDataService.cacheLatestQuote(quote);

        assertEquals("AAPL", normalized.ticker());
        assertEquals(QuoteStatus.LIVE, normalized.status());
        verify(valueOperations).set(
                "quote:latest:AAPL",
                "{\"quote\":true}",
                Duration.ofSeconds(60));
        verify(valueOperations).set(
                "quote:last-known:AAPL",
                "{\"quote\":true}",
                Duration.ofSeconds(604800));
    }

    @Test
    void testGetLatestQuoteFallsBackToLastKnownQuoteAsStale() throws Exception {
        MarketQuote cached = new MarketQuote(
                "QUOTE",
                "AAPL",
                BigDecimal.valueOf(180),
                Instant.parse("2026-10-20T19:00:00Z"),
                Instant.parse("2026-10-20T19:00:01Z"),
                "test");
        when(valueOperations.get("quote:latest:AAPL")).thenReturn(null);
        when(valueOperations.get("quote:last-known:AAPL")).thenReturn("{\"quote\":true}");
        when(objectMapper.readValue("{\"quote\":true}", MarketQuote.class)).thenReturn(cached);

        MarketQuote result = marketDataService.getLatestQuote("aapl").orElseThrow();

        assertEquals(BigDecimal.valueOf(180), result.price());
        assertEquals(QuoteStatus.STALE, result.status());
    }

    @Test
    void testGetQuoteSnapshotMarksKnownClosedMarket() throws Exception {
        MarketQuote cached = new MarketQuote(
                "QUOTE",
                "AAPL",
                BigDecimal.valueOf(180),
                Instant.parse("2026-10-20T19:00:00Z"),
                Instant.parse("2026-10-20T19:00:01Z"),
                "test");
        Instrument instrument = new Instrument();
        instrument.setTicker("AAPL");
        when(valueOperations.get("quote:latest:AAPL")).thenReturn(null);
        when(valueOperations.get("quote:last-known:AAPL")).thenReturn("{\"quote\":true}");
        when(objectMapper.readValue("{\"quote\":true}", MarketQuote.class)).thenReturn(cached);
        when(instrumentMapper.findByTicker("AAPL")).thenReturn(instrument);
        doThrow(new MarketClosedException("market closed"))
                .when(marketSessionService).assertMarketOpen(instrument);

        MarketQuote result = marketDataService.getQuoteSnapshot("AAPL");

        assertEquals(QuoteStatus.CLOSED, result.status());
        assertEquals(BigDecimal.valueOf(180), result.price());
    }

    @Test
    void testGetSectorCatalogDelegatesToMarketClient() {
        MarketSectorCatalog expected = sectorCatalog();
        when(marketDataClient.fetchSectorCatalog()).thenReturn(expected);

        MarketSectorCatalog result = marketDataService.getSectorCatalog();

        assertEquals(expected, result);
        verify(marketDataClient).fetchSectorCatalog();
    }

    @Test
    void testGetHeatmapUsesSharedQuoteCacheAndMarksMissingQuotesUnavailable()
            throws Exception {
        when(marketDataClient.fetchSectorCatalog()).thenReturn(sectorCatalog());
        MarketQuote cached = new MarketQuote(
                "QUOTE",
                "XOM",
                BigDecimal.valueOf(120),
                Instant.parse("2026-10-21T15:59:00Z"),
                Instant.parse("2026-10-21T15:59:01Z"),
                "test");
        when(valueOperations.get("quote:latest:XOM")).thenReturn("{\"quote\":true}");
        when(objectMapper.readValue("{\"quote\":true}", MarketQuote.class)).thenReturn(cached);

        MarketHeatmapResponse result = marketDataService.getHeatmap(null);

        assertEquals(1, result.sectors().size());
        assertEquals(2, result.sectors().get(0).tickers().size());
        assertEquals(QuoteStatus.LIVE, result.sectors().get(0).tickers().get(0).status());
        assertEquals(QuoteStatus.UNAVAILABLE, result.sectors().get(0).tickers().get(1).status());
        assertEquals(BigDecimal.valueOf(120), result.sectors().get(0).tickers().get(0).price());
    }

    // Verifies invalid tickers are rejected before cache or client calls.
    @Test
    void testGetHistoryRejectsInvalidTicker() {
        assertThrows(ObjectInvalidException.class, () -> marketDataService.getHistory("aapl$", "1m"));

        verifyNoInteractions(marketDataClient);
        verify(valueOperations, never()).get(any());
    }

    // Verifies null tickers are rejected before cache or client calls.
    @Test
    void testGetHistoryRejectsNullTicker() {
        assertThrows(ObjectInvalidException.class, () -> marketDataService.getHistory(null, "1m"));

        verifyNoInteractions(marketDataClient);
        verify(valueOperations, never()).get(any());
    }

    // Verifies ticker is normalized (trimmed/uppercased) for cache key and client calls.
    @Test
    void testGetHistoryNormalizesTickerForCacheAndClient() throws Exception {
        when(valueOperations.get("history:AAPL")).thenReturn(null);
        when(marketDataClient.fetchHistory("AAPL", "5y")).thenReturn(history("AAPL", barsForSlicing()));
        when(objectMapper.writeValueAsString(any(HistoryResponse.class))).thenReturn("{\"ok\":true}");

        HistoryResponse result = marketDataService.getHistory(" aapl ", "all");

        assertEquals("AAPL", result.ticker());
        verify(valueOperations).get("history:AAPL");
        verify(marketDataClient).fetchHistory("AAPL", "5y");
    }

    // Verifies supported period 1m returns only bars within the latest-one-month window.
    @Test
    void testGetHistorySupportsOneMonthPeriod() throws Exception {
        when(valueOperations.get("history:AAPL")).thenReturn(null);
        when(marketDataClient.fetchHistory("AAPL", "5y")).thenReturn(history("AAPL", barsForSlicing()));
        when(objectMapper.writeValueAsString(any(HistoryResponse.class))).thenReturn("{\"ok\":true}");

        HistoryResponse result = marketDataService.getHistory("AAPL", "1m");

        assertEquals("1m", result.period());
        assertEquals(2, result.bars().size());
        assertTrue(result.bars().stream().allMatch(b -> !b.date().isBefore(LocalDate.of(2025, 11, 30))));
    }

    // Verifies supported period all returns all available bars.
    @Test
    void testGetHistorySupportsAllPeriod() throws Exception {
        when(valueOperations.get("history:AAPL")).thenReturn(null);
        when(marketDataClient.fetchHistory("AAPL", "5y")).thenReturn(history("AAPL", barsForSlicing()));
        when(objectMapper.writeValueAsString(any(HistoryResponse.class))).thenReturn("{\"ok\":true}");

        HistoryResponse result = marketDataService.getHistory("AAPL", "all");

        assertEquals("all", result.period());
        assertEquals(3, result.bars().size());
    }

    // Verifies unsupported periods raise ObjectInvalidException.
    @Test
    void testGetHistoryRejectsUnsupportedPeriod() throws Exception {
        when(valueOperations.get("history:AAPL")).thenReturn(null);
        when(marketDataClient.fetchHistory("AAPL", "5y")).thenReturn(history("AAPL", barsForSlicing()));
        when(objectMapper.writeValueAsString(any(HistoryResponse.class))).thenReturn("{\"ok\":true}");

        assertThrows(ObjectInvalidException.class, () -> marketDataService.getHistory("AAPL", "7d"));
    }

    // Verifies a valid Redis cache hit returns parsed history and skips client calls.
    @Test
    void testGetHistoryUsesCacheHit() throws Exception {
        HistoryResponse cachedHistory = history("AAPL", barsForSlicing());

        when(valueOperations.get("history:AAPL")).thenReturn("{\"cached\":true}");
        when(objectMapper.readValue("{\"cached\":true}", HistoryResponse.class)).thenReturn(cachedHistory);

        HistoryResponse result = marketDataService.getHistory("AAPL", "all");

        assertEquals(3, result.bars().size());
        verify(marketDataClient, never()).fetchHistory(any(), any());
    }

    // Verifies a Redis cache miss falls back to client and caches serialized history.
    @Test
    void testGetHistoryFallsBackOnCacheMissAndWritesCache() throws Exception {
        when(valueOperations.get("history:AAPL")).thenReturn(null);
        when(marketDataClient.fetchHistory("AAPL", "5y")).thenReturn(history("AAPL", barsForSlicing()));
        when(objectMapper.writeValueAsString(any(HistoryResponse.class))).thenReturn("{\"fresh\":true}");

        HistoryResponse result = marketDataService.getHistory("AAPL", "all");

        assertEquals("AAPL", result.ticker());
        verify(valueOperations).set("history:AAPL", "{\"fresh\":true}", Duration.ofHours(6));
    }

    // Verifies Redis read failures degrade to client fallback instead of failing the request.
    @Test
    void testGetHistoryHandlesRedisReadFailure() throws Exception {
        when(valueOperations.get("history:AAPL")).thenThrow(new RuntimeException("redis down"));
        when(marketDataClient.fetchHistory("AAPL", "5y")).thenReturn(history("AAPL", barsForSlicing()));
        when(objectMapper.writeValueAsString(any(HistoryResponse.class))).thenReturn("{\"fresh\":true}");

        HistoryResponse result = marketDataService.getHistory("AAPL", "all");

        assertEquals(3, result.bars().size());
        verify(marketDataClient).fetchHistory("AAPL", "5y");
    }

    // Verifies Redis write failures do not fail successful history responses.
    @Test
    void testGetHistoryIgnoresRedisWriteFailure() throws Exception {
        when(valueOperations.get("history:AAPL")).thenReturn(null);
        when(marketDataClient.fetchHistory("AAPL", "5y")).thenReturn(history("AAPL", barsForSlicing()));
        when(objectMapper.writeValueAsString(any(HistoryResponse.class))).thenReturn("{\"fresh\":true}");
        org.mockito.Mockito.doThrow(new RuntimeException("write failed"))
                .when(valueOperations).set(eq("history:AAPL"), eq("{\"fresh\":true}"), eq(Duration.ofHours(6)));

        HistoryResponse result = marketDataService.getHistory("AAPL", "all");

        assertEquals("AAPL", result.ticker());
        verify(marketDataClient).fetchHistory("AAPL", "5y");
    }

    // Verifies serialization failures do not prevent returning fresh market data.
    @Test
    void testGetHistoryIgnoresSerializationFailure() throws Exception {
        when(valueOperations.get("history:AAPL")).thenReturn(null);
        HistoryResponse fresh = history("AAPL", barsForSlicing());
        when(marketDataClient.fetchHistory("AAPL", "5y")).thenReturn(fresh);
        when(objectMapper.writeValueAsString(fresh))
                .thenThrow(new JsonProcessingException("serialization failed") { });

        HistoryResponse result = marketDataService.getHistory("AAPL", "all");

        assertEquals(fresh.ticker(), result.ticker());
        assertEquals(fresh.bars(), result.bars());
        verify(marketDataClient).fetchHistory("AAPL", "5y");
        verify(valueOperations).set("cooldown:history:AAPL", "1", Duration.ofSeconds(180));
    }

    // Verifies cached NOT_FOUND marker raises ObjectNotFoundException without client call.
    @Test
    void testGetHistoryThrowsForCachedNotFoundMarker() {
        when(valueOperations.get("history:AAPL")).thenReturn("NOT_FOUND");

        assertThrows(ObjectNotFoundException.class, () -> marketDataService.getHistory("AAPL", "all"));

        verify(marketDataClient, never()).fetchHistory(any(), any());
    }

    // Verifies client not-found responses are cached as NOT_FOUND markers with short TTL.
    @Test
    void testGetHistoryCachesNotFoundMarkerFromClientException() {
        when(valueOperations.get("history:AAPL")).thenReturn(null);
        when(marketDataClient.fetchHistory("AAPL", "5y")).thenThrow(new ObjectNotFoundException("No market data"));

        assertThrows(ObjectNotFoundException.class, () -> marketDataService.getHistory("AAPL", "all"));

        verify(valueOperations).set("history:AAPL", "NOT_FOUND", Duration.ofMinutes(10));
    }

    // Verifies unreadable cached JSON falls back to MarketDataClient.
    @Test
    void testGetHistoryFallsBackWhenCachedJsonUnreadable() throws Exception {
        when(valueOperations.get("history:AAPL")).thenReturn("{\"bad\":true}");
        when(objectMapper.readValue("{\"bad\":true}", HistoryResponse.class))
                .thenThrow(new JsonProcessingException("bad json") { });
        when(marketDataClient.fetchHistory("AAPL", "5y")).thenReturn(history("AAPL", barsForSlicing()));
        when(objectMapper.writeValueAsString(any(HistoryResponse.class))).thenReturn("{\"fresh\":true}");

        HistoryResponse result = marketDataService.getHistory("AAPL", "all");

        assertEquals(3, result.bars().size());
        verify(marketDataClient).fetchHistory("AAPL", "5y");
    }

    // Verifies empty bar lists from MarketDataClient raise MarketDataUnavailableException.
    @Test
    void testGetHistoryRejectsEmptyBarsFromClient() throws Exception {
        when(valueOperations.get("history:AAPL")).thenReturn(null);
        when(marketDataClient.fetchHistory("AAPL", "5y")).thenReturn(history("AAPL", List.of()));

        assertThrows(MarketDataUnavailableException.class, () -> marketDataService.getHistory("AAPL", "all"));

        verify(objectMapper, never()).writeValueAsString(any(HistoryResponse.class));
    }

    // Verifies null bars from MarketDataClient raise MarketDataUnavailableException.
    @Test
    void testGetHistoryRejectsNullBarsFromClient() throws Exception {
        when(valueOperations.get("history:AAPL")).thenReturn(null);
        when(marketDataClient.fetchHistory("AAPL", "5y")).thenReturn(new HistoryResponse("AAPL", "5y", null));

        assertThrows(MarketDataUnavailableException.class, () -> marketDataService.getHistory("AAPL", "all"));

        verify(objectMapper, never()).writeValueAsString(any(HistoryResponse.class));
    }

    // Verifies all supported period aliases are accepted by the period switch.
    @Test
    void testGetHistorySupportsPeriodAliases() throws Exception {
        when(valueOperations.get("history:AAPL")).thenReturn(null);
        when(marketDataClient.fetchHistory("AAPL", "5y")).thenReturn(history("AAPL", barsForSlicing()));
        when(objectMapper.writeValueAsString(any(HistoryResponse.class))).thenReturn("{\"fresh\":true}");

        for (String period : List.of("1mo", "3m", "3mo", "6m", "6mo", "1y", "2y", "max", "5y")) {
            HistoryResponse result = marketDataService.getHistory("AAPL", period);
            assertEquals(period, result.period());
        }
    }

    private HistoryResponse history(String ticker, List<PriceBar> bars) {
        return new HistoryResponse(ticker, "5y", bars);
    }

    private MarketSectorCatalog sectorCatalog() {
        return new MarketSectorCatalog(
                "US",
                "configured_large_cap_order",
                "us-large-cap-v1",
                Instant.parse("2026-10-21T16:00:00Z"),
                List.of(new MarketSector(
                        "energy",
                        "Energy",
                        List.of(
                                new MarketSectorTicker("XOM", 1),
                                new MarketSectorTicker("CVX", 2)))));
    }

    private List<PriceBar> barsForSlicing() {
        return List.of(
                priceBar(LocalDate.of(2025, 10, 15), "100"),
                priceBar(LocalDate.of(2025, 12, 1), "110"),
                priceBar(LocalDate.of(2025, 12, 31), "120")
        );
    }

    private PriceBar priceBar(LocalDate date, String close) {
        BigDecimal value = new BigDecimal(close);
        return new PriceBar(date, value, value, value, value, 1000L);
    }
}
