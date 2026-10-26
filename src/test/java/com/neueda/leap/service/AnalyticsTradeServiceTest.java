package com.neueda.leap.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import com.neueda.leap.mapper.analytics.TradeMapper;
import com.neueda.leap.model.analytics.TradeSearchQuery;
import com.neueda.leap.model.analytics.projection.TradeProjection;
import com.neueda.leap.service.analytics.TradeService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AnalyticsTradeServiceTest {

    @Test
    void snapshotIncludesOlderTradesBeyondFirstHundredAndPreservesDateRange() {
        TradeMapper mapper = mock(TradeMapper.class);
        TradeService service = new TradeService(mapper);
        LocalDate from = LocalDate.of(2026, 7, 26);
        LocalDate to = LocalDate.of(2026, 10, 26);
        TradeSearchQuery query = new TradeSearchQuery(from, to, null, null, null, 0, 50);
        List<TradeProjection> rows = IntStream.range(0, 150).mapToObj(index ->
                new TradeProjection(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        "AAPL", "Apple", "BUY", BigDecimal.ONE, BigDecimal.TEN,
                        BigDecimal.TEN, OffsetDateTime.parse(
                                index < 100 ? "2026-10-26T20:00:00Z" : "2026-08-03T20:00:00Z"),
                        BigDecimal.ZERO)).toList();
        when(mapper.findSnapshotTrades(query)).thenReturn(rows);

        var result = service.getSnapshotTrades(from, to);

        assertEquals(150, result.size());
        assertEquals(rows.get(149).executedAt(), result.get(149).executedAt());
        verify(mapper).findSnapshotTrades(query);
        verifyNoMoreInteractions(mapper);
    }
}
