package com.neueda.leap.service;

import com.neueda.leap.client.MarketDataClient;
import com.neueda.leap.dto.response.InstrumentResponse;
import com.neueda.leap.dto.response.MarketQuote;
import com.neueda.leap.dto.response.QuoteStatus;
import com.neueda.leap.exception.ObjectAlreadyExistsException;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.mapper.ExchangeMapper;
import com.neueda.leap.mapper.InstrumentMapper;
import com.neueda.leap.model.Instrument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class InstrumentServiceTest {

    @Mock
    private InstrumentMapper instrumentMapper;

    @Mock
    private MarketDataClient marketDataClient;
    
    @Mock
    private ExchangeMapper exchangeMapper;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private MarketDataService marketDataService;
    


    private InstrumentService instrumentService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);
        instrumentService = new InstrumentService(
                instrumentMapper,
                marketDataClient,
                exchangeMapper,
                redis,
                marketDataService,
                120,
                20,
                180);
    }

    // Verifies getInstrumentById returns an instrument when one exists.
    @Test
    void testGetInstrumentByIdReturnsInstrumentWhenFound() {
        UUID instrumentId = UUID.randomUUID();
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);
        instrument.setTicker("AAPL");

        when(instrumentMapper.findById(instrumentId)).thenReturn(instrument);

        Instrument result = instrumentService.getInstrumentById(instrumentId);

        assertNotNull(result);
        assertEquals(instrumentId, result.getInstrumentId());
        assertEquals("AAPL", result.getTicker());
        verify(instrumentMapper, times(1)).findById(instrumentId);
    }

    // Verifies getInstrumentById throws when id is null.
    @Test
    void testGetInstrumentByIdThrowsWhenIdIsNull() {
        assertThrows(ObjectNotFoundException.class, () -> instrumentService.getInstrumentById(null));

        verify(instrumentMapper, never()).findById(any(UUID.class));
    }

    // Verifies getInstrumentById throws when instrument does not exist.
    @Test
    void testGetInstrumentByIdThrowsWhenMissing() {
        UUID instrumentId = UUID.randomUUID();

        when(instrumentMapper.findById(instrumentId)).thenReturn(null);

        assertThrows(ObjectNotFoundException.class, () -> instrumentService.getInstrumentById(instrumentId));

        verify(instrumentMapper, times(1)).findById(instrumentId);
    }

    // Verifies getInstrumentByTicker returns an instrument when one exists.
    @Test
    void testGetInstrumentByTickerReturnsInstrumentWhenFound() {
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(UUID.randomUUID());
        instrument.setTicker("MSFT");

        when(instrumentMapper.findByTicker("MSFT")).thenReturn(instrument);

        Instrument result = instrumentService.getInstrumentByTicker("MSFT");

        assertNotNull(result);
        assertEquals("MSFT", result.getTicker());
        verify(instrumentMapper, times(1)).findByTicker("MSFT");
    }

    // Verifies getInstrumentByTicker throws when ticker is null.
    @Test
    void testGetInstrumentByTickerThrowsWhenTickerIsNull() {
        assertThrows(ObjectNotFoundException.class, () -> instrumentService.getInstrumentByTicker(null));

        verify(instrumentMapper, never()).findByTicker(any(String.class));
    }

    // Verifies getInstrumentByTicker throws when ticker is empty.
    @Test
    void testGetInstrumentByTickerThrowsWhenTickerIsEmpty() {
        assertThrows(ObjectNotFoundException.class, () -> instrumentService.getInstrumentByTicker(""));

        verify(instrumentMapper, never()).findByTicker(any(String.class));
    }

    // Verifies getInstrumentByTicker throws when instrument does not exist.
    @Test
    void testGetInstrumentByTickerThrowsWhenMissing() {
        when(instrumentMapper.findByTicker("UNKNOWN")).thenReturn(null);

        assertThrows(ObjectNotFoundException.class, () -> instrumentService.getInstrumentByTicker("UNKNOWN"));

        verify(instrumentMapper, times(1)).findByTicker("UNKNOWN");
    }

    // Verifies addInstrument succeeds and sets updatedAt when null.
    @Test
    void testAddInstrumentReturnsInstrumentWhenValid() {
        UUID instrumentId = UUID.randomUUID();
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);
        instrument.setTicker("AAPL");
        instrument.setName("Apple Inc.");
        instrument.setExchange("XNYS");
        instrument.setCurrentPrice(BigDecimal.valueOf(180.50));
        instrument.setUpdatedAt(null);

        when(instrumentMapper.findById(instrumentId)).thenReturn(null);
        when(instrumentMapper.findByTicker("AAPL")).thenReturn(null);
        when(instrumentMapper.addInstrument(instrument)).thenReturn(1);

        Instrument result = instrumentService.addInstrument(instrument);

        assertNotNull(result);
        assertEquals(instrumentId, result.getInstrumentId());
        assertNotNull(result.getUpdatedAt());
        verify(instrumentMapper, times(1)).findById(instrumentId);
        verify(instrumentMapper, times(1)).findByTicker("AAPL");
        verify(instrumentMapper, times(1)).addInstrument(instrument);
    }

    // Verifies addInstrument keeps existing updatedAt value.
    @Test
    void testAddInstrumentPreservesUpdatedAtWhenProvided() {
        UUID instrumentId = UUID.randomUUID();
        OffsetDateTime updatedAt = OffsetDateTime.now().minusDays(1);
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);
        instrument.setTicker("MSFT");
        instrument.setName("Microsoft");
        instrument.setExchange("XNAS");
        instrument.setCurrentPrice(BigDecimal.valueOf(320.10));
        instrument.setUpdatedAt(updatedAt);

        when(instrumentMapper.findById(instrumentId)).thenReturn(null);
        when(instrumentMapper.findByTicker("MSFT")).thenReturn(null);
        when(instrumentMapper.addInstrument(instrument)).thenReturn(1);

        Instrument result = instrumentService.addInstrument(instrument);

        assertEquals(updatedAt, result.getUpdatedAt());
        verify(instrumentMapper, times(1)).addInstrument(instrument);
    }

    // Verifies addInstrument throws when instrument id is null.
    @Test
    void testAddInstrumentThrowsWhenInstrumentIdIsNull() {
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(null);
        instrument.setTicker("AAPL");
        instrument.setExchange("XNYS");
        instrument.setCurrentPrice(BigDecimal.ONE);

        assertThrows(ObjectNotFoundException.class, () -> instrumentService.addInstrument(instrument));

        verify(instrumentMapper, never()).findById(any(UUID.class));
    }

    // Verifies addInstrument throws for duplicate instrument id.
    @Test
    void testAddInstrumentThrowsWhenInstrumentIdAlreadyExists() {
        UUID instrumentId = UUID.randomUUID();
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);
        instrument.setTicker("AAPL");
        instrument.setExchange("XNYS");
        instrument.setCurrentPrice(BigDecimal.ONE);

        when(instrumentMapper.findById(instrumentId)).thenReturn(new Instrument());

        assertThrows(ObjectAlreadyExistsException.class, () -> instrumentService.addInstrument(instrument));

        verify(instrumentMapper, times(1)).findById(instrumentId);
        verify(instrumentMapper, never()).findByTicker(any(String.class));
    }

    // Verifies addInstrument throws when ticker is null.
    @Test
    void testAddInstrumentThrowsWhenTickerIsNull() {
        UUID instrumentId = UUID.randomUUID();
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);
        instrument.setTicker(null);
        instrument.setExchange("XNYS");
        instrument.setCurrentPrice(BigDecimal.ONE);

        when(instrumentMapper.findById(instrumentId)).thenReturn(null);

        assertThrows(ObjectNotFoundException.class, () -> instrumentService.addInstrument(instrument));

        verify(instrumentMapper, times(1)).findById(instrumentId);
        verify(instrumentMapper, never()).findByTicker(any(String.class));
    }

    // Verifies addInstrument throws for duplicate ticker.
    @Test
    void testAddInstrumentThrowsWhenTickerAlreadyExists() {
        UUID instrumentId = UUID.randomUUID();
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);
        instrument.setTicker("AAPL");
        instrument.setExchange("XNYS");
        instrument.setCurrentPrice(BigDecimal.ONE);

        when(instrumentMapper.findById(instrumentId)).thenReturn(null);
        when(instrumentMapper.findByTicker("AAPL")).thenReturn(new Instrument());

        assertThrows(ObjectAlreadyExistsException.class, () -> instrumentService.addInstrument(instrument));

        verify(instrumentMapper, times(1)).findById(instrumentId);
        verify(instrumentMapper, times(1)).findByTicker("AAPL");
    }

    // Verifies addInstrument throws when exchange is null.
    @Test
    void testAddInstrumentThrowsWhenExchangeIsNull() {
        UUID instrumentId = UUID.randomUUID();
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);
        instrument.setTicker("AAPL");
        instrument.setExchange(null);
        instrument.setCurrentPrice(BigDecimal.ONE);

        when(instrumentMapper.findById(instrumentId)).thenReturn(null);
        when(instrumentMapper.findByTicker("AAPL")).thenReturn(null);

        assertThrows(ObjectNotFoundException.class, () -> instrumentService.addInstrument(instrument));

        verify(instrumentMapper, times(1)).findById(instrumentId);
        verify(instrumentMapper, times(1)).findByTicker("AAPL");
    }

    // Verifies addInstrument throws when current price is zero.
    @Test
    void testAddInstrumentThrowsWhenCurrentPriceIsZero() {
        UUID instrumentId = UUID.randomUUID();
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);
        instrument.setTicker("AAPL");
        instrument.setExchange("XNYS");
        instrument.setCurrentPrice(BigDecimal.ZERO);

        when(instrumentMapper.findById(instrumentId)).thenReturn(null);
        when(instrumentMapper.findByTicker("AAPL")).thenReturn(null);

        assertThrows(ObjectNotFoundException.class, () -> instrumentService.addInstrument(instrument));

        verify(instrumentMapper, times(1)).findById(instrumentId);
        verify(instrumentMapper, times(1)).findByTicker("AAPL");
    }

    // Verifies addInstrument throws when current price is negative.
    @Test
    void testAddInstrumentThrowsWhenCurrentPriceIsNegative() {
        UUID instrumentId = UUID.randomUUID();
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);
        instrument.setTicker("AAPL");
        instrument.setExchange("XNYS");
        instrument.setCurrentPrice(BigDecimal.valueOf(-1));

        when(instrumentMapper.findById(instrumentId)).thenReturn(null);
        when(instrumentMapper.findByTicker("AAPL")).thenReturn(null);

        assertThrows(ObjectNotFoundException.class, () -> instrumentService.addInstrument(instrument));

        verify(instrumentMapper, times(1)).findById(instrumentId);
        verify(instrumentMapper, times(1)).findByTicker("AAPL");
    }

    // Verifies addInstrument throws when mapper insert fails.
    @Test
    void testAddInstrumentThrowsWhenInsertFails() {
        UUID instrumentId = UUID.randomUUID();
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);
        instrument.setTicker("AAPL");
        instrument.setName("Apple Inc.");
        instrument.setExchange("XNYS");
        instrument.setCurrentPrice(BigDecimal.valueOf(180.50));

        when(instrumentMapper.findById(instrumentId)).thenReturn(null);
        when(instrumentMapper.findByTicker("AAPL")).thenReturn(null);
        when(instrumentMapper.addInstrument(instrument)).thenReturn(0);

        assertThrows(ObjectNotProcessedException.class, () -> instrumentService.addInstrument(instrument));

        verify(instrumentMapper, times(1)).addInstrument(instrument);
    }

    // Verifies updatePrice succeeds for existing instruments.
    @Test
    void testUpdatePriceSucceedsWhenInstrumentExists() {
        UUID instrumentId = UUID.randomUUID();
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);
        instrument.setTicker("AAPL");
        instrument.setCurrentPrice(BigDecimal.valueOf(100));

        when(instrumentMapper.findById(instrumentId)).thenReturn(instrument);
        when(instrumentMapper.updatePrice(any(Instrument.class))).thenReturn(1);

        assertDoesNotThrow(() -> instrumentService.updatePrice(instrumentId, BigDecimal.valueOf(120.25)));

        assertEquals(BigDecimal.valueOf(120.25), instrument.getCurrentPrice());
        assertNotNull(instrument.getUpdatedAt());
        verify(instrumentMapper, times(1)).findById(instrumentId);
        verify(instrumentMapper, times(1)).updatePrice(any(Instrument.class));
    }

    // Verifies updatePrice throws when instrument id does not exist.
    @Test
    void testUpdatePriceThrowsWhenInstrumentMissing() {
        UUID instrumentId = UUID.randomUUID();

        when(instrumentMapper.findById(instrumentId)).thenReturn(null);

        assertThrows(ObjectNotFoundException.class,
                () -> instrumentService.updatePrice(instrumentId, BigDecimal.valueOf(120.25)));

        verify(instrumentMapper, times(1)).findById(instrumentId);
        verify(instrumentMapper, never()).updatePrice(any(Instrument.class));
    }

    @Test
    void testRefreshPriceIfStaleRejectsLastKnownQuoteForTrading() {
        UUID instrumentId = UUID.randomUUID();
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);
        instrument.setTicker("AAPL");
        instrument.setCurrentPrice(BigDecimal.valueOf(180));
        instrument.setUpdatedAt(OffsetDateTime.now());

        MarketQuote staleQuote = new MarketQuote(
                "QUOTE",
                "AAPL",
                BigDecimal.valueOf(181),
                Instant.now(),
                Instant.now(),
                "test",
                QuoteStatus.STALE);

        when(instrumentMapper.findById(instrumentId)).thenReturn(instrument);
        when(marketDataService.getLatestQuote("AAPL")).thenReturn(Optional.of(staleQuote));

        assertThrows(
                ObjectInvalidException.class,
                () -> instrumentService.refreshPriceIfStale(instrumentId));

        verify(marketDataService, never()).getHistory(any(), any());
        verify(instrumentMapper, never()).updatePrice(any(Instrument.class));
    }

    @Test
    void testRefreshPriceIfStaleFetchesLiveQuoteWhenNoCachedQuote() {
        UUID instrumentId = UUID.randomUUID();
        Instant quoteTime = Instant.now();
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);
        instrument.setTicker("BTC-USD");
        instrument.setCurrentPrice(BigDecimal.valueOf(575.5));
        instrument.setUpdatedAt(OffsetDateTime.now().minusHours(1));

        MarketQuote liveQuote = new MarketQuote(
                "QUOTE",
                "BTC-USD",
                BigDecimal.valueOf(81000),
                quoteTime,
                quoteTime,
                "yfinance-rest",
                QuoteStatus.LIVE);

        when(instrumentMapper.findById(instrumentId)).thenReturn(instrument);
        when(marketDataService.getLatestQuote("BTC-USD")).thenReturn(Optional.empty());
        when(marketDataService.fetchLiveQuote("BTC-USD")).thenReturn(liveQuote);
        when(instrumentMapper.updatePrice(instrument)).thenReturn(1);

        Instrument refreshed = instrumentService.refreshPriceIfStale(instrumentId);

        assertEquals(BigDecimal.valueOf(81000), refreshed.getCurrentPrice());
        assertEquals(OffsetDateTime.ofInstant(quoteTime, java.time.ZoneOffset.UTC), refreshed.getUpdatedAt());
        verify(marketDataService).fetchLiveQuote("BTC-USD");
        verify(instrumentMapper).updatePrice(instrument);
    }

    // Verifies updatePrice throws when mapper update fails.
    @Test
    void testUpdatePriceThrowsWhenUpdateFails() {
        UUID instrumentId = UUID.randomUUID();
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        when(instrumentMapper.findById(instrumentId)).thenReturn(instrument);
        when(instrumentMapper.updatePrice(any(Instrument.class))).thenReturn(0);

        assertThrows(ObjectNotProcessedException.class,
                () -> instrumentService.updatePrice(instrumentId, BigDecimal.valueOf(120.25)));

        verify(instrumentMapper, times(1)).findById(instrumentId);
        verify(instrumentMapper, times(1)).updatePrice(any(Instrument.class));
    }

    // Verifies findAllInstruments maps entities to response DTOs.
    @Test
    void testFindAllInstrumentsReturnsMappedResponses() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        Instrument first = new Instrument();
        first.setInstrumentId(firstId);
        first.setTicker("AAPL");
        first.setName("Apple Inc.");
        first.setExchange("XNYS");
        first.setCurrentPrice(BigDecimal.valueOf(195.25));

        Instrument second = new Instrument();
        second.setInstrumentId(secondId);
        second.setTicker("MSFT");
        second.setName("Microsoft Corp.");
        second.setExchange("XNAS");
        second.setCurrentPrice(BigDecimal.valueOf(401.75));

        when(instrumentMapper.findAll()).thenReturn(List.of(first, second));

        List<InstrumentResponse> responses = instrumentService.findAllInstruments();

        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals(firstId, responses.get(0).getInstrumentId());
        assertEquals("AAPL", responses.get(0).getTicker());
        assertEquals(secondId, responses.get(1).getInstrumentId());
        assertEquals("MSFT", responses.get(1).getTicker());
        verify(instrumentMapper, times(1)).findAll();
    }

    // Verifies findAllInstruments returns an empty list when mapper returns none.
    @Test
    void testFindAllInstrumentsReturnsEmptyListWhenNoData() {
        when(instrumentMapper.findAll()).thenReturn(List.of());

        List<InstrumentResponse> responses = instrumentService.findAllInstruments();

        assertNotNull(responses);
        assertEquals(0, responses.size());
        verify(instrumentMapper, times(1)).findAll();
    }
}
