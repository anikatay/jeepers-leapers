package com.neueda.leap.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.mapper.analytics.DashboardMapper;
import com.neueda.leap.model.analytics.DashboardFlow;
import com.neueda.leap.model.analytics.DashboardFlowQuery;
import com.neueda.leap.model.analytics.DashboardOutcome;
import com.neueda.leap.model.analytics.DashboardRange;
import com.neueda.leap.model.analytics.DashboardSummary;
import com.neueda.leap.model.analytics.projection.DashboardFlowProjection;
import com.neueda.leap.model.analytics.projection.DashboardOutcomeProjection;
import com.neueda.leap.model.analytics.projection.DashboardSummaryProjection;

@Service
public class DashboardService {

    private static final BigDecimal FLAT_BAND = new BigDecimal("0.005");
    private static final String DEFAULT_BUCKET = "day";

    private final DashboardMapper dashboardMapper;

    public DashboardService(DashboardMapper dashboardMapper) {
        this.dashboardMapper = dashboardMapper;
    }

    public DashboardSummary getSummary(DashboardRange range) {
        DashboardSummaryProjection projection = dashboardMapper.findSummary(validateRange(range));
        requireProjection(projection, "dashboard summary");

        return new DashboardSummary(
                required(projection.totalVolume(), "total volume"),
                required(projection.totalTrades(), "total trades"),
                required(projection.activeAccounts(), "active accounts"),
                required(projection.realizedPnl(), "realized P&L"),
                required(projection.unrealizedPnl(), "unrealized P&L"),
                required(projection.totalPnl(), "total P&L"));
    }

    public List<DashboardFlow> getFlow(DashboardFlowQuery query) {
        DashboardFlowQuery validatedQuery = validateFlowQuery(query);
        List<DashboardFlowProjection> projections = dashboardMapper.findFlow(validatedQuery);
        if (projections == null) {
            throw new ObjectNotProcessedException("Analytics mapper returned no flow result");
        }

        return projections.stream().map(this::toFlow).toList();
    }

    public List<DashboardOutcome> getOutcomes(DashboardRange range) {
        List<DashboardOutcomeProjection> projections = dashboardMapper.findOutcomes(validateRange(range));
        if (projections == null) {
            throw new ObjectNotProcessedException("Analytics mapper returned no outcome result");
        }

        return projections.stream().map(this::toOutcome).toList();
    }

    private DashboardFlow toFlow(DashboardFlowProjection projection) {
        requireProjection(projection, "dashboard flow item");
        return new DashboardFlow(
                required(projection.bucketDate(), "flow bucket date"),
                required(projection.buyVolume(), "buy volume"),
                required(projection.sellVolume(), "sell volume"),
                required(projection.netVolume(), "net volume"),
                required(projection.trades(), "flow trade count"));
    }

    private DashboardOutcome toOutcome(DashboardOutcomeProjection projection) {
        requireProjection(projection, "dashboard outcome item");
        BigDecimal notional = required(projection.notional(), "outcome notional");
        BigDecimal pnl = required(projection.pnl(), "outcome P&L");
        BigDecimal flatThreshold = notional.abs().multiply(FLAT_BAND);
        String outcome = pnl.abs().compareTo(flatThreshold) <= 0
                ? "FLAT"
                : pnl.signum() > 0 ? "PROFITABLE" : "LOSING";

        return new DashboardOutcome(
                required(projection.accountId(), "outcome account ID"),
                required(projection.trades(), "outcome trade count"),
                notional,
                pnl,
                outcome);
    }

    private DashboardRange validateRange(DashboardRange range) {
        if (range == null) {
            throw new ObjectInvalidException("Dashboard date range is required");
        }
        LocalDate from = range.from();
        LocalDate to = range.to();
        if (from != null && to != null && from.isAfter(to)) {
            throw new ObjectInvalidException("from date must be before or equal to the to date");
        }
        return range;
    }

    private DashboardFlowQuery validateFlowQuery(DashboardFlowQuery query) {
        if (query == null) {
            throw new ObjectInvalidException("Dashboard flow query is required");
        }
        validateRange(new DashboardRange(query.from(), query.to()));

        String bucket = query.bucket() == null || query.bucket().isBlank()
                ? DEFAULT_BUCKET
                : query.bucket().trim().toLowerCase(Locale.ROOT);
        if (!bucket.equals("day") && !bucket.equals("week") && !bucket.equals("month")) {
            throw new ObjectInvalidException("Bucket must be day, week, or month");
        }
        return new DashboardFlowQuery(query.from(), query.to(), bucket);
    }

    private void requireProjection(Object projection, String description) {
        if (projection == null) {
            throw new ObjectNotProcessedException("Analytics mapper returned a null " + description);
        }
    }

    private <T> T required(T value, String field) {
        if (value == null) {
            throw new ObjectNotProcessedException("Analytics mapper returned null " + field);
        }
        return value;
    }
}