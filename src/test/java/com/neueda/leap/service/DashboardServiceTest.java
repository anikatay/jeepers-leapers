package com.neueda.leap.service;

import java.util.List;

import com.neueda.leap.mapper.analytics.DashboardMapper;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.analytics.TradePage;
import com.neueda.leap.service.analytics.InstrumentService;
import com.neueda.leap.service.analytics.TradeService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    @Test
    void defaultSnapshotDoesNotFetchMarketHistory() {
        InstrumentService instruments = mock(InstrumentService.class);
        TradeService trades = mock(TradeService.class);
        DashboardService service = new DashboardService(mock(DashboardMapper.class), instruments, trades);
        Instrument instrument = new Instrument();
        instrument.setTicker("AAPL");
        when(instruments.getAllInstruments()).thenReturn(List.of(instrument));
        when(trades.getTrades(any())).thenReturn(new TradePage(List.of(), 0, 0, 100));

        var snapshot = service.getSnapshot(null);

        assertEquals(List.of(instrument), snapshot.instruments());
        assertTrue(snapshot.history().isEmpty());
        verify(instruments).getAllInstruments();
        verifyNoMoreInteractions(instruments);
    }
}
