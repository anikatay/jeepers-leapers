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
import com.neueda.leap.dto.response.analytics.DashboardSummaryResponse;
import com.neueda.leap.model.analytics.DashboardFlow;
import com.neueda.leap.model.analytics.DashboardFlowQuery;
import com.neueda.leap.model.analytics.DashboardOutcome;
import com.neueda.leap.model.analytics.DashboardRange;
import com.neueda.leap.model.analytics.DashboardSummary;
import com.neueda.leap.service.DashboardService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final DashboardService dashboardService;

    public AnalyticsController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard/snapshot")
    public String getDashboardSnapshot(@RequestBody DashboardSnapshotRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
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
    public String getInstruments() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @GetMapping("/instruments/{ticker}/prices")
    public String getInstrumentPrices(
            @PathVariable String ticker,
            @RequestBody PriceHistoryRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @GetMapping("/instruments/{ticker}/intraday")
    public String getInstrumentIntraday(
            @PathVariable String ticker,
            @RequestBody IntradayRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @GetMapping("/trades")
    public String getTrades(@Valid @RequestBody TradeSearchRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PostMapping("/admin/refresh")
    public String triggerAnalyticsRefresh() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @GetMapping("/admin/refresh/{runId}")
    public String getAnalyticsRefreshStatus(@PathVariable String runId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private DashboardRange toModel(DashboardRangeRequest request) {
        return request == null ? null : new DashboardRange(request.from(), request.to());
    }

    private DashboardFlowQuery toModel(DashboardFlowRequest request) {
        return request == null ? null : new DashboardFlowQuery(request.from(), request.to(), request.bucket());
    }

}