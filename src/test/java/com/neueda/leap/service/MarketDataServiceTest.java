package com.neueda.leap.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.client.MarketDataClient;
import com.neueda.leap.dto.response.HistoryResponse;
import com.neueda.leap.dto.response.PriceBar;
import com.neueda.leap.exception.MarketDataUnavailableException;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    private MarketDataService marketDataService;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
        marketDataService = new MarketDataService(marketDataClient, redis, objectMapper);
    }

    // Verifies invalid tickers are rejected before cache or client calls.
    @Test
    void testGetHistoryRejectsInvalidTicker() {
        assertThrows(ObjectInvalidException.class, () -> marketDataService.getHistory("aapl$", "1m"));

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

    private HistoryResponse history(String ticker, List<PriceBar> bars) {
        return new HistoryResponse(ticker, "5y", bars);
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
