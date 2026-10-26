package com.neueda.leap.service;

import com.neueda.leap.dto.response.AdminDashboardRow;
import com.neueda.leap.dto.response.PaginatedTradeResponse;
import com.neueda.leap.dto.response.TradeResponse;
import com.neueda.leap.mapper.AdminDashboardMapper;
import com.neueda.leap.mapper.TradeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private AdminDashboardMapper dashboardMapper;

    @Mock
    private TradeMapper tradeMapper;

    @Test
    void getDashboardResetsStalePageToNewestAccountsPage() {
        List<AdminDashboardRow> newestAccounts = List.of(new AdminDashboardRow());
        when(dashboardMapper.countDashboardRows(null)).thenReturn(30L);
        when(dashboardMapper.findDashboardRows(null, 25, 0)).thenReturn(newestAccounts);

        AdminDashboardService service = new AdminDashboardService(dashboardMapper, tradeMapper);
        var response = service.getDashboard(null, 4, 25);

        assertEquals(newestAccounts, response.items());
        assertEquals(0, response.page());
        assertEquals(25, response.size());
        assertEquals(30L, response.totalItems());
        assertEquals(2, response.totalPages());
        verify(dashboardMapper).findDashboardRows(null, 25, 0);
    }

    @Test
    void getTradesPageUsesDefaultSizeAndCalculatesOffset() {
        UUID accountId = UUID.randomUUID();
        List<TradeResponse> trades = List.of(new TradeResponse(
                UUID.randomUUID(), accountId, UUID.randomUUID(), "AAPL", "Apple Inc.", "BUY",
                java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, java.math.BigDecimal.TEN,
                java.time.OffsetDateTime.now()));
        when(tradeMapper.countTradesForAccountPage(org.mockito.ArgumentMatchers.eq(accountId), org.mockito.ArgumentMatchers.any())).thenReturn(21L);
        when(tradeMapper.findTradesForAccountPage(
            org.mockito.ArgumentMatchers.eq(accountId), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(10),
                org.mockito.ArgumentMatchers.eq(10))).thenReturn(trades);

        PaginatedTradeResponse response = new AdminDashboardService(dashboardMapper, tradeMapper)
                .getTradesPage(accountId, 1, null);

        assertEquals(trades, response.items());
        assertEquals(1, response.page());
        assertEquals(10, response.size());
        assertEquals(21L, response.totalItems());
        assertEquals(3, response.totalPages());
    }
}