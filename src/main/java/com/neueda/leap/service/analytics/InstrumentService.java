package com.neueda.leap.service.analytics;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.neueda.leap.dto.response.HistoryResponse;
import com.neueda.leap.exception.MarketDataUnavailableException;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.mapper.analytics.InstrumentMapper;
import com.neueda.leap.model.analytics.IntradayQuery;
import com.neueda.leap.model.analytics.PriceBar;
import com.neueda.leap.model.analytics.PriceHistory;
import com.neueda.leap.model.analytics.PriceHistoryQuery;
import com.neueda.leap.dto.response.analytics.PriceHistoryResponse;
import com.neueda.leap.model.analytics.projection.InstrumentProjection;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.service.MarketDataService;

@Service("analyticsInstrumentService")
public class InstrumentService {

    private static final Pattern TICKER_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9.\\-_=]{0,31}$");
    private static final String DEFAULT_INTERVAL = "day";
    private static final String DEFAULT_PERIOD = "1y";
    private static final String FULL_PERIOD = "5y";

    private final InstrumentMapper instrumentMapper;
    private final MarketDataService marketDataService;

    public InstrumentService(
            InstrumentMapper instrumentMapper,
            MarketDataService marketDataService) {
        this.instrumentMapper = instrumentMapper;
        this.marketDataService = marketDataService;
    }

    public List<Instrument> getAllInstruments() {
        List<InstrumentProjection> projections = instrumentMapper.findAll();
        if (projections == null) {
            throw new ObjectNotProcessedException("Analytics mapper returned no instrument result");
        }
        return projections.stream().map(this::toInstrument).toList();
    }

    public Instrument getInstrumentByTicker(String rawTicker) {
        String ticker = normalizeTicker(rawTicker);
        InstrumentProjection projection = instrumentMapper.findByTicker(ticker);
        if (projection == null) {
            throw new ObjectNotFoundException("Instrument not found: " + ticker);
        }
        return toInstrument(projection);
    }

    public PriceHistory getPriceHistory(PriceHistoryQuery query) {
        if (query == null) {
            throw new ObjectInvalidException("Price history query is required");
        }

        String ticker = normalizeTicker(query.ticker());
        validateDateRange(query.from(), query.to());
        String interval = normalizeInterval(query.interval());
        getInstrumentByTicker(ticker);

        String period = query.from() == null && query.to() == null
                ? DEFAULT_PERIOD
                : FULL_PERIOD;
        HistoryResponse history = marketDataService.getHistory(ticker, period);
        if (history == null || history.bars() == null) {
            throw new MarketDataUnavailableException(
                    "Market data service returned no price history for " + ticker);
        }

        List<PriceBar> bars = history.bars().stream()
                .map(this::toPriceBar)
                .filter(bar -> isWithinRange(bar.date(), query.from(), query.to()))
                .sorted(Comparator.comparing(PriceBar::date))
                .toList();
        if (bars.isEmpty()) {
            throw new ObjectNotFoundException("No price data available for " + ticker);
        }

        return new PriceHistory(ticker, interval, aggregate(bars, interval));
    }

    public List<PriceBar> getIntraday(IntradayQuery query) {
        if (query == null) {
            throw new ObjectInvalidException("Intraday query is required");
        }

        String ticker = normalizeTicker(query.ticker());
        if (query.date() == null) {
            throw new ObjectInvalidException("Intraday date is required");
        }
        getInstrumentByTicker(ticker);

        throw new MarketDataUnavailableException(
                "Intraday price data is not available for " + ticker);
    }

    private Instrument toInstrument(InstrumentProjection projection) {
        if (projection == null) {
            throw new ObjectNotProcessedException("Analytics mapper returned a null instrument");
        }
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(projection.instrumentId());
        instrument.setTicker(projection.ticker());
        instrument.setName(projection.name());
        instrument.setExchange(projection.exchangeId());
        instrument.setCurrentPrice(projection.currentPrice());
        instrument.setUpdatedAt(projection.updatedAt());
        return instrument;
    }

    private PriceBar toPriceBar(com.neueda.leap.dto.response.PriceBar source) {
        if (source == null
                || source.date() == null
                || source.open() == null
                || source.high() == null
                || source.low() == null
                || source.close() == null) {
            throw new MarketDataUnavailableException("Market data service returned an invalid price bar");
        }
        return new PriceBar(
                source.date(),
                source.open(),
                source.high(),
                source.low(),
                source.close(),
                source.volume());
    }

    private List<PriceBar> aggregate(List<PriceBar> bars, String interval) {
        if (DEFAULT_INTERVAL.equals(interval)) {
            return bars;
        }

        Map<LocalDate, List<PriceBar>> buckets = new LinkedHashMap<>();
        for (PriceBar bar : bars) {
            LocalDate bucketDate = bucketDate(bar.date(), interval);
            buckets.computeIfAbsent(bucketDate, ignored -> new ArrayList<>()).add(bar);
        }

        return buckets.entrySet().stream()
                .map(entry -> aggregateBucket(entry.getKey(), entry.getValue()))
                .toList();
    }

    private PriceBar aggregateBucket(LocalDate bucketDate, List<PriceBar> bars) {
        PriceBar first = bars.get(0);
        PriceBar last = bars.get(bars.size() - 1);
        BigDecimal high = bars.stream()
                .map(PriceBar::high)
                .max(BigDecimal::compareTo)
                .orElseThrow();
        BigDecimal low = bars.stream()
                .map(PriceBar::low)
                .min(BigDecimal::compareTo)
                .orElseThrow();

        return new PriceBar(
                bucketDate,
                first.open(),
                high,
                low,
                last.close(),
                sumVolume(bars));
    }

    private Long sumVolume(List<PriceBar> bars) {
        long total = 0;
        for (PriceBar bar : bars) {
            if (bar.volume() == null) {
                return null;
            }
            total = Math.addExact(total, bar.volume());
        }
        return total;
    }

    private LocalDate bucketDate(LocalDate date, String interval) {
        return switch (interval) {
            case "week" -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "month" -> date.withDayOfMonth(1);
            default -> throw new ObjectInvalidException("Unsupported price interval: " + interval);
        };
    }

    private boolean isWithinRange(LocalDate date, LocalDate from, LocalDate to) {
        return (from == null || !date.isBefore(from))
                && (to == null || !date.isAfter(to));
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ObjectInvalidException("from date must be before or equal to the to date");
        }
    }

    private String normalizeInterval(String rawInterval) {
        if (rawInterval == null || rawInterval.isBlank()) {
            return DEFAULT_INTERVAL;
        }

        return switch (rawInterval.trim().toLowerCase(Locale.ROOT)) {
            case "day", "daily", "1d" -> "day";
            case "week", "weekly", "1w", "1wk" -> "week";
            case "month", "monthly", "1m", "1mo" -> "month";
            default -> throw new ObjectInvalidException("Unsupported price interval: " + rawInterval);
        };
    }

    private String normalizeTicker(String rawTicker) {
        if (rawTicker == null || rawTicker.isBlank()) {
            throw new ObjectInvalidException("Ticker is required");
        }

        String ticker = rawTicker.trim().toUpperCase(Locale.ROOT);
        if (!TICKER_PATTERN.matcher(ticker).matches()) {
            throw new ObjectInvalidException("Invalid ticker: " + rawTicker);
        }
        return ticker;
    }
}