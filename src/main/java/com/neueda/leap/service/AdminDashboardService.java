package com.neueda.leap.service;

import com.neueda.leap.dto.response.AdminDashboardResponse;
import com.neueda.leap.dto.response.AdminDashboardRow;
import com.neueda.leap.dto.response.TradeResponse;
import com.neueda.leap.mapper.AdminDashboardMapper;
import com.neueda.leap.mapper.TradeMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AdminDashboardService {
    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_TRADE_PAGE_SIZE = 200;

    private final AdminDashboardMapper dashboardMapper;
    private final TradeMapper tradeMapper;

    public AdminDashboardService(AdminDashboardMapper dashboardMapper, TradeMapper tradeMapper) {
        this.dashboardMapper = dashboardMapper;
        this.tradeMapper = tradeMapper;
    }

    public AdminDashboardResponse getDashboard(String search, Integer page, Integer size) {
        int normalizedPage = page == null || page < 0 ? 0 : page;
        int normalizedSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        long totalItems = dashboardMapper.countDashboardRows(normalizedSearch);
        int totalPages = totalItems == 0 ? 0 : (int) ((totalItems + normalizedSize - 1) / normalizedSize);
        if (totalPages > 0 && normalizedPage >= totalPages) {
            normalizedPage = 0;
        }
        List<AdminDashboardRow> items = totalItems == 0
                ? List.of()
                : dashboardMapper.findDashboardRows(normalizedSearch, normalizedSize, normalizedPage * normalizedSize);
        return new AdminDashboardResponse(items, normalizedPage, normalizedSize, totalItems, totalPages);
    }

    public List<TradeResponse> getTrades(UUID accountId, Integer limit, Integer offset) {
        int normalizedLimit = limit == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(limit, 1), MAX_TRADE_PAGE_SIZE);
        int normalizedOffset = offset == null || offset < 0 ? 0 : offset;
        return tradeMapper.findAllTradesForAccountWithInstrument(accountId, normalizedLimit, normalizedOffset);
    }
}
