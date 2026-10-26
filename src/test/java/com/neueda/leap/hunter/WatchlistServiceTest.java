package com.neueda.leap.hunter;

import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.hunter.catalog.GicsSector;
import com.neueda.leap.hunter.catalog.Universe;
import com.neueda.leap.mapper.HunterSpecMapper;
import com.neueda.leap.mapper.WatchlistMapper;
import com.neueda.leap.model.HunterSpecRecord;
import com.neueda.leap.model.WatchlistItemRecord;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WatchlistServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SPEC_ID = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC);

    private final WatchlistMapper watchlistMapper = mock(WatchlistMapper.class);
    private final HunterSpecMapper specMapper = mock(HunterSpecMapper.class);
    private final WatchlistService service = new WatchlistService(watchlistMapper, specMapper, new Universe(), CLOCK);

    @Test
    void addsATickerOnceAndThenReportsItAsAlreadyThere() {
        when(specMapper.findOwned(SPEC_ID, USER_ID)).thenReturn(spec("AI infrastructure"));
        when(watchlistMapper.insert(eq(USER_ID), eq("NVDA"), any(), eq(SPEC_ID))).thenReturn(1);

        WatchlistService.Added added = service.add(USER_ID, "nvda", SPEC_ID);

        assertTrue(added.created());
        assertEquals("Nvidia", added.item().companyName());
        assertEquals(GicsSector.INFORMATION_TECHNOLOGY, added.item().gicsSector());
        assertEquals("AI infrastructure", added.item().sourceSpec().name());

        when(watchlistMapper.exists(USER_ID, "NVDA")).thenReturn(true);
        when(watchlistMapper.findAll(USER_ID)).thenReturn(List.of(row("NVDA", SPEC_ID, "AI infrastructure")));

        WatchlistService.Added again = service.add(USER_ID, "NVDA", null);

        assertFalse(again.created());
        verify(watchlistMapper).insert(any(), anyString(), any(), any());
    }

    @Test
    void storesTheYahooFormOfTheTicker() {
        when(watchlistMapper.insert(eq(USER_ID), eq("BRK-B"), any(), isNull())).thenReturn(1);

        assertEquals("BRK-B", service.add(USER_ID, "BRK.B", null).item().ticker());
    }

    @Test
    void rejectsATickerOutsideTheUniverse() {
        assertThrows(ObjectNotFoundException.class, () -> service.add(USER_ID, "TSM", null));
        verify(watchlistMapper, never()).insert(any(), anyString(), any(), any());
    }

    @Test
    void rejectsAnotherUsersHunterAsTheSource() {
        assertThrows(ObjectNotFoundException.class, () -> service.add(USER_ID, "NVDA", SPEC_ID));
    }

    @Test
    void refusesAHundredAndFirstStock() {
        when(watchlistMapper.countByUser(USER_ID)).thenReturn(HunterLimits.MAX_WATCHLIST_ITEMS);

        assertThrows(LimitReachedException.class, () -> service.add(USER_ID, "NVDA", null));
    }

    @Test
    void listsItemsWithNamesFromTheUniverseAndKeepsThemWhenTheirHunterIsGone() {
        when(watchlistMapper.findAll(USER_ID)).thenReturn(List.of(
                row("NVDA", null, null),
                row("GONE", null, null)));

        List<WatchlistService.Item> items = service.list(USER_ID);

        assertEquals("Nvidia", items.get(0).companyName());
        assertNull(items.get(0).sourceSpec());
        assertNull(items.get(1).companyName());
        assertNull(items.get(1).gicsSector());
    }

    @Test
    void removesByTheYahooForm() {
        service.remove(USER_ID, "brk.b");

        verify(watchlistMapper).delete(USER_ID, "BRK-B");
    }

    private static HunterSpecRecord spec(String name) {
        return new HunterSpecRecord(SPEC_ID, USER_ID, name, "", "{}", null, null);
    }

    private static WatchlistItemRecord row(String ticker, UUID sourceSpecId, String sourceSpecName) {
        WatchlistItemRecord row = new WatchlistItemRecord();
        row.setTicker(ticker);
        row.setAddedAt(OffsetDateTime.now(CLOCK));
        row.setSourceSpecId(sourceSpecId);
        row.setSourceSpecName(sourceSpecName);
        return row;
    }
}
