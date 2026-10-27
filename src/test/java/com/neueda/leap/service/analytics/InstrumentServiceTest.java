package com.neueda.leap.service.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.neueda.leap.dto.response.HistoryResponse;
import com.neueda.leap.exception.MarketDataUnavailableException;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.mapper.analytics.InstrumentMapper;
import com.neueda.leap.model.analytics.IntradayQuery;
import com.neueda.leap.model.analytics.PriceHistory;
import com.neueda.leap.model.analytics.PriceHistoryQuery;
import com.neueda.leap.model.analytics.projection.InstrumentProjection;
import com.neueda.leap.service.MarketDataService;

@ExtendWith(MockitoExtension.class)
class InstrumentServiceTest {

    @Mock
    private InstrumentMapper instrumentMapper;

    @Mock
    private MarketDataService marketDataService;

    @Test
    void mapsAnalyticsInstrumentProjectionsToTheExistingInstrumentModel() {
        UUID instrumentId = UUID.randomUUID();
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-10-21T15:00:00Z");
        when(instrumentMapper.findAll()).thenReturn(List.of(new InstrumentProjection(
                instrumentId,
                "AAPL",
                "Apple Inc.",
                "NASDAQ",
                new BigDecimal("226.40"),
                updatedAt)));

        var result = new InstrumentService(instrumentMapper, marketDataService).getAllInstruments();

        assertEquals(instrumentId, result.get(0).getInstrumentId());
        assertEquals("AAPL", result.get(0).getTicker());
        assertEquals("NASDAQ", result.get(0).getExchange());
        assertEquals(new BigDecimal("226.40"), result.get(0).getCurrentPrice());
        assertEquals(updatedAt, result.get(0).getUpdatedAt());
    }

    @Test
    void filtersAndAggregatesDailyHistoryIntoWeeklyBars() {
        when(instrumentMapper.findByTicker("AAPL")).thenReturn(instrument("AAPL"));
        when(marketDataService.getHistory("AAPL", "5y")).thenReturn(new HistoryResponse(
                "AAPL",
                "5y",
                List.of(
                        marketBar("2026-01-05", "10", "12", "9", "11", 100L),
                        marketBar("2026-01-06", "11", "13", "10", "12", 200L),
                        marketBar("2026-01-12", "12", "14", "11", "13", 300L))));

        PriceHistory result = new InstrumentService(instrumentMapper, marketDataService)
                .getPriceHistory(new PriceHistoryQuery(
                        "aapl",
                        LocalDate.parse("2026-01-05"),
                        LocalDate.parse("2026-01-12"),
                        "week"));

        assertEquals("AAPL", result.ticker());
        assertEquals("week", result.interval());
        assertEquals(2, result.bars().size());
        assertEquals(LocalDate.parse("2026-01-05"), result.bars().get(0).date());
        assertEquals(new BigDecimal("10"), result.bars().get(0).open());
        assertEquals(new BigDecimal("13"), result.bars().get(0).high());
        assertEquals(new BigDecimal("9"), result.bars().get(0).low());
        assertEquals(new BigDecimal("12"), result.bars().get(0).close());
        assertEquals(300L, result.bars().get(0).volume());
        assertEquals(new BigDecimal("13"), result.bars().get(1).close());
    }

    @Test
    void rejectsAnInvertedPriceHistoryRangeBeforeQuerying() {
        InstrumentService service = new InstrumentService(instrumentMapper, marketDataService);

        assertThrows(ObjectInvalidException.class, () -> service.getPriceHistory(new PriceHistoryQuery(
                "AAPL",
                LocalDate.parse("2026-01-12"),
                LocalDate.parse("2026-01-05"),
                "day")));

        verifyNoInteractions(instrumentMapper, marketDataService);
    }

    @Test
    void reportsIntradayDataAsUnavailableUntilADataSourceExists() {
        when(instrumentMapper.findByTicker("AAPL")).thenReturn(instrument("AAPL"));

        InstrumentService service = new InstrumentService(instrumentMapper, marketDataService);

        assertThrows(MarketDataUnavailableException.class, () -> service.getIntraday(
                new IntradayQuery("aapl", LocalDate.parse("2026-01-05"))));

        verify(instrumentMapper).findByTicker("AAPL");
        verifyNoInteractions(marketDataService);
    }

    private InstrumentProjection instrument(String ticker) {
        return new InstrumentProjection(
                UUID.randomUUID(),
                ticker,
                "Apple Inc.",
                "NASDAQ",
                new BigDecimal("226.40"),
                OffsetDateTime.parse("2026-10-21T15:00:00Z"));
    }

    private com.neueda.leap.dto.response.PriceBar marketBar(
            String date,
            String open,
            String high,
            String low,
            String close,
            Long volume) {
        return new com.neueda.leap.dto.response.PriceBar(
                LocalDate.parse(date),
                new BigDecimal(open),
                new BigDecimal(high),
                new BigDecimal(low),
                new BigDecimal(close),
                volume);
    }
}