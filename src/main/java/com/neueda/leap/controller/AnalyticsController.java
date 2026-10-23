package com.neueda.leap.controller;

import java.util.List;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.neueda.leap.dto.request.analytics.DashboardFlowRequest;
import com.neueda.leap.dto.request.analytics.DashboardRangeRequest;
import com.neueda.leap.dto.request.analytics.DashboardSnapshotRequest;
import com.neueda.leap.dto.request.analytics.IntradayRequest;
import com.neueda.leap.dto.request.analytics.PriceHistoryRequest;
import com.neueda.leap.dto.request.analytics.TradeSearchRequest;
import com.neueda.leap.dto.response.analytics.DashboardFlowResponse;
import com.neueda.leap.dto.response.analytics.DashboardOutcomeResponse;
import com.neueda.leap.dto.response.analytics.DashboardSnapshotResponse;
import com.neueda.leap.dto.response.analytics.DashboardSummaryResponse;
import com.neueda.leap.dto.response.analytics.InstrumentResponse;
import com.neueda.leap.dto.response.analytics.PriceBarResponse;
import com.neueda.leap.dto.response.analytics.PriceHistoryResponse;
import com.neueda.leap.dto.response.analytics.RefreshResponse;
import com.neueda.leap.dto.response.analytics.TradePageResponse;
import com.neueda.leap.model.analytics.DashboardFlowQuery;
import com.neueda.leap.model.analytics.DashboardOutcome;
import com.neueda.leap.model.analytics.DashboardRange;
import com.neueda.leap.model.analytics.DashboardSnapshot;
import com.neueda.leap.model.analytics.DashboardSnapshotQuery;
import com.neueda.leap.model.analytics.DashboardSummary;
import com.neueda.leap.model.analytics.IntradayQuery;
import com.neueda.leap.model.analytics.PriceHistory;
import com.neueda.leap.model.analytics.PriceHistoryQuery;
import com.neueda.leap.model.analytics.Refresh;
import com.neueda.leap.model.analytics.TradeSearchQuery;
import com.neueda.leap.model.analytics.TradePage;
import com.neueda.leap.service.DashboardService;
import com.neueda.leap.service.analytics.InstrumentService;
import com.neueda.leap.service.analytics.RefreshService;
import com.neueda.leap.service.analytics.TradeService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final DashboardService dashboardService;
    private final InstrumentService instrumentService;
    private final TradeService tradeService;
    private final RefreshService refreshService;

    public AnalyticsController(
            DashboardService dashboardService,
            InstrumentService instrumentService,
            TradeService tradeService,
            RefreshService refreshService) {
        this.dashboardService = dashboardService;
        this.instrumentService = instrumentService;
        this.tradeService = tradeService;
        this.refreshService = refreshService;
    }

    @GetMapping("/dashboard/snapshot")
    public DashboardSnapshotResponse getDashboardSnapshot(
            @RequestBody(required = false) DashboardSnapshotRequest request) {
            DashboardSnapshot snapshot = dashboardService.getSnapshot(toModel(request));
        return DashboardSnapshotResponse.from(snapshot);
    }

    @GetMapping("/dashboard/summary")
    public DashboardSummaryResponse getDashboardSummary(@RequestBody DashboardRangeRequest request) {
        DashboardSummary summary = dashboardService.getSummary(toModel(request));
        return DashboardSummaryResponse.from(summary);
    }

    @GetMapping("/dashboard/flow")
    public List<DashboardFlowResponse> getDashboardFlow(@RequestBody DashboardFlowRequest request) {
        return dashboardService.getFlow(toModel(request)).stream()
                .map(DashboardFlowResponse::from)
                .toList();
    }

    @GetMapping("/dashboard/outcomes")
    public List<DashboardOutcomeResponse> getDashboardOutcomes(
            @RequestBody DashboardRangeRequest request) {
        return dashboardService.getOutcomes(toModel(request)).stream()
            .map(DashboardOutcomeResponse::from)
                .toList();
    }

    @GetMapping("/instruments")
    public List<InstrumentResponse> getInstruments() {
        return instrumentService.getAllInstruments().stream()
                .map(InstrumentResponse::from)
                .toList();
    }

    @GetMapping("/instruments/{ticker}/prices")
    public PriceHistoryResponse getInstrumentPrices(
            @PathVariable String ticker,
            @RequestBody PriceHistoryRequest request) {
        PriceHistory history = instrumentService.getPriceHistory(toModel(ticker, request));
        return PriceHistoryResponse.from(history);
    }

    @GetMapping("/instruments/{ticker}/intraday")
    public List<PriceBarResponse> getInstrumentIntraday(
            @PathVariable String ticker,
            @RequestBody IntradayRequest request) {
        return instrumentService.getIntraday(toModel(ticker, request)).stream()
                .map(PriceBarResponse::from)
                .toList();
    }

    @GetMapping("/trades")
    public TradePageResponse getTrades(@Valid @RequestBody TradeSearchRequest request) {
        TradePage trades = tradeService.getTrades(toModel(request));
        return TradePageResponse.from(trades);
    }

    @PostMapping("/admin/refresh")
    public RefreshResponse triggerAnalyticsRefresh() {
        Refresh refresh = refreshService.triggerRefresh();
        return RefreshResponse.from(refresh);
    }

    @GetMapping("/admin/refresh/{runId}")
    public RefreshResponse getAnalyticsRefreshStatus(@PathVariable String runId) {
        Refresh refresh = refreshService.getRefreshStatus(runId);
        return RefreshResponse.from(refresh);
    }

    private DashboardRange toModel(DashboardRangeRequest request) {
        return request == null ? null : new DashboardRange(request.from(), request.to());
    }

    private DashboardFlowQuery toModel(DashboardFlowRequest request) {
        return request == null ? null : new DashboardFlowQuery(request.from(), request.to(), request.bucket());
    }

    private DashboardSnapshotQuery toModel(DashboardSnapshotRequest request) {
        return request == null
                ? null
                : new DashboardSnapshotQuery(request.from(), request.to(), request.symbols());
    }

    private PriceHistoryQuery toModel(String ticker, PriceHistoryRequest request) {
        return request == null
                ? new PriceHistoryQuery(ticker, null, null, null)
                : new PriceHistoryQuery(ticker, request.from(), request.to(), request.interval());
    }

    private IntradayQuery toModel(String ticker, IntradayRequest request) {
        return new IntradayQuery(ticker, request == null ? null : request.date());
    }

    private TradeSearchQuery toModel(TradeSearchRequest request) {
        return request == null
                ? null
                : new TradeSearchQuery(
                        request.from(),
                        request.to(),
                        request.ticker(),
                        request.accountId(),
                        request.side(),
                        request.page(),
                        request.size());
    }

}