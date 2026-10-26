package com.neueda.leap.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.mapper.analytics.DashboardMapper;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.analytics.DashboardFlow;
import com.neueda.leap.model.analytics.DashboardFlowQuery;
import com.neueda.leap.model.analytics.DashboardOutcome;
import com.neueda.leap.model.analytics.DashboardRange;
import com.neueda.leap.model.analytics.DashboardSnapshot;
import com.neueda.leap.model.analytics.DashboardSnapshotQuery;
import com.neueda.leap.model.analytics.DashboardSummary;
import com.neueda.leap.model.analytics.PriceBar;
import com.neueda.leap.model.analytics.PriceHistory;
import com.neueda.leap.model.analytics.PriceHistoryQuery;
import com.neueda.leap.model.analytics.Trade;
import com.neueda.leap.model.analytics.TradePage;
import com.neueda.leap.model.analytics.TradeSearchQuery;
import com.neueda.leap.model.analytics.projection.DashboardFlowProjection;
import com.neueda.leap.model.analytics.projection.DashboardOutcomeProjection;
import com.neueda.leap.model.analytics.projection.DashboardSummaryProjection;
import com.neueda.leap.service.analytics.InstrumentService;
import com.neueda.leap.service.analytics.TradeService;

@Service
public class DashboardService {

    private static final BigDecimal FLAT_BAND = new BigDecimal("0.005");
    private static final String DEFAULT_BUCKET = "day";
    private static final Pattern TICKER_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9.\\-_=]{0,31}$");
    private static final int SNAPSHOT_TRADE_PAGE_SIZE = 100;

    private final DashboardMapper dashboardMapper;
    private final InstrumentService instrumentService;
    private final TradeService tradeService;

    public DashboardService(
            DashboardMapper dashboardMapper,
            InstrumentService instrumentService,
            TradeService tradeService) {
        this.dashboardMapper = dashboardMapper;
        this.instrumentService = instrumentService;
        this.tradeService = tradeService;
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

    public DashboardSnapshot getSnapshot(DashboardSnapshotQuery query) {
        DashboardSnapshotQuery validatedQuery = validateSnapshotQuery(query);
        List<Instrument> instruments = selectInstruments(validatedQuery.symbols());
        Map<String, List<PriceBar>> history = new LinkedHashMap<>();
        Map<String, List<PriceBar>> intraday = new LinkedHashMap<>();

        List<Instrument> historyInstruments = validatedQuery.symbols() == null || validatedQuery.symbols().isEmpty()
                ? List.of()
                : instruments;
        for (Instrument instrument : historyInstruments) {
            String ticker = instrument.getTicker();
            PriceHistory priceHistory = instrumentService.getPriceHistory(
                    new PriceHistoryQuery(ticker, validatedQuery.from(), validatedQuery.to(), "day"));
            history.put(ticker, priceHistory.bars());
            intraday.put(ticker, List.of());
        }

        TradePage tradePage = tradeService.getTrades(new TradeSearchQuery(
                validatedQuery.from(),
                validatedQuery.to(),
                null,
                null,
                null,
                0,
                SNAPSHOT_TRADE_PAGE_SIZE));
        LocalDate asOf = latestDate(history, LocalDate.now(ZoneOffset.UTC));

        return new DashboardSnapshot(asOf, instruments, history, intraday, tradePage.trades());
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

    private DashboardSnapshotQuery validateSnapshotQuery(DashboardSnapshotQuery query) {
        if (query == null) {
            return new DashboardSnapshotQuery(null, null, null);
        }
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw new ObjectInvalidException("from date must be before or equal to the to date");
        }
        return query;
    }

    private List<Instrument> selectInstruments(List<String> requestedSymbols) {
        List<Instrument> available = instrumentService.getAllInstruments();
        if (requestedSymbols == null || requestedSymbols.isEmpty()) {
            return available;
        }

        Map<String, Instrument> byTicker = available.stream()
                .collect(Collectors.toMap(
                        instrument -> instrument.getTicker().toUpperCase(Locale.ROOT),
                        instrument -> instrument,
                        (left, right) -> left,
                        LinkedHashMap::new));
        return requestedSymbols.stream()
                .map(this::normalizeTicker)
                .distinct()
                .map(ticker -> findInstrument(byTicker, ticker))
                .toList();
    }

    private Instrument findInstrument(Map<String, Instrument> byTicker, String ticker) {
        Instrument instrument = byTicker.get(ticker);
        if (instrument == null) {
            throw new ObjectNotFoundException("Instrument not found: " + ticker);
        }
        return instrument;
    }

    private String normalizeTicker(String rawTicker) {
        if (rawTicker == null || rawTicker.isBlank()) {
            throw new ObjectInvalidException("Snapshot symbol is required");
        }
        String ticker = rawTicker.trim().toUpperCase(Locale.ROOT);
        if (!TICKER_PATTERN.matcher(ticker).matches()) {
            throw new ObjectInvalidException("Invalid snapshot symbol: " + rawTicker);
        }
        return ticker;
    }

    private LocalDate latestDate(Map<String, List<PriceBar>> history, LocalDate fallback) {
        return history.values().stream()
                .flatMap(List::stream)
                .map(PriceBar::date)
                .max(LocalDate::compareTo)
                .orElse(fallback);
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