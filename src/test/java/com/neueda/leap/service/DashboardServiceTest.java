package com.neueda.leap.service;

import java.util.Collections;
import java.util.List;

import com.neueda.leap.mapper.analytics.DashboardMapper;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.analytics.Trade;
import com.neueda.leap.service.analytics.InstrumentService;
import com.neueda.leap.service.analytics.TradeService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    @Test
    void defaultSnapshotIncludesAllTradesWithoutFetchingMarketHistory() {
        InstrumentService instruments = mock(InstrumentService.class);
        TradeService trades = mock(TradeService.class);
        DashboardService service = new DashboardService(mock(DashboardMapper.class), instruments, trades);
        Instrument instrument = new Instrument();
        instrument.setTicker("AAPL");
        when(instruments.getAllInstruments()).thenReturn(List.of(instrument));
        List<Trade> allTrades = Collections.nCopies(150, mock(Trade.class));
        when(trades.getSnapshotTrades(null, null)).thenReturn(allTrades);

        var snapshot = service.getSnapshot(null);

        assertEquals(List.of(instrument), snapshot.instruments());
        assertEquals(allTrades, snapshot.trades());
        assertEquals(150, snapshot.trades().size());
        assertTrue(snapshot.history().isEmpty());
        verify(instruments).getAllInstruments();
        verifyNoMoreInteractions(instruments);
        verify(trades).getSnapshotTrades(null, null);
        verifyNoMoreInteractions(trades);
    }
}
