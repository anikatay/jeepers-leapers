package com.neueda.leap.service.analytics;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.mapper.analytics.TradeMapper;
import com.neueda.leap.model.analytics.Trade;
import com.neueda.leap.model.analytics.TradePage;
import com.neueda.leap.model.analytics.TradeSearchQuery;
import com.neueda.leap.model.analytics.projection.TradeProjection;

@Service("analyticsTradeService")
public class TradeService {

    private static final Pattern TICKER_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9.\\-_=]{0,31}$");
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 100;

    private final TradeMapper tradeMapper;

    public TradeService(TradeMapper tradeMapper) {
        this.tradeMapper = tradeMapper;
    }

    public TradePage getTrades(TradeSearchQuery query) {
        TradeSearchQuery validatedQuery = validateQuery(query);
        List<TradeProjection> projections = tradeMapper.findTrades(validatedQuery);
        if (projections == null) {
            throw new ObjectNotProcessedException("Analytics mapper returned no trade result");
        }

        long total = tradeMapper.countTrades(validatedQuery);
        return new TradePage(
                projections.stream().map(this::toTrade).toList(),
                total,
                validatedQuery.page(),
                validatedQuery.size());
    }

    public List<Trade> getSnapshotTrades(LocalDate from, LocalDate to) {
        TradeSearchQuery query = validateQuery(
                new TradeSearchQuery(from, to, null, null, null, DEFAULT_PAGE, DEFAULT_SIZE));
        List<TradeProjection> projections = tradeMapper.findSnapshotTrades(query);
        if (projections == null) {
            throw new ObjectNotProcessedException("Analytics mapper returned no snapshot trade result");
        }
        return projections.stream().map(this::toTrade).toList();
    }

    private TradeSearchQuery validateQuery(TradeSearchQuery query) {
        if (query == null) {
            throw new ObjectInvalidException("Trade search query is required");
        }

        LocalDate from = query.from();
        LocalDate to = query.to();
        if (from != null && to != null && from.isAfter(to)) {
            throw new ObjectInvalidException("from date must be before or equal to the to date");
        }

        String ticker = normalizeTicker(query.ticker());
        String side = normalizeSide(query.side());
        int page = query.page() == null ? DEFAULT_PAGE : query.page();
        int size = query.size() == null ? DEFAULT_SIZE : query.size();
        if (page < 0) {
            throw new ObjectInvalidException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new ObjectInvalidException("size must be between 1 and " + MAX_SIZE);
        }

        return new TradeSearchQuery(from, to, ticker, query.accountId(), side, page, size);
    }

    private Trade toTrade(TradeProjection projection) {
        if (projection == null) {
            throw new ObjectNotProcessedException("Analytics mapper returned a null trade");
        }
        return new Trade(
                required(projection.tradeId(), "trade ID"),
                required(projection.accountId(), "account ID"),
                required(projection.instrumentId(), "instrument ID"),
                required(projection.ticker(), "ticker"),
                required(projection.name(), "instrument name"),
                required(projection.side(), "trade side"),
                required(projection.quantity(), "quantity"),
                required(projection.executionPrice(), "execution price"),
                required(projection.tradeValue(), "trade value"),
                required(projection.executedAt(), "execution time"),
                required(projection.pnl(), "P&L"));
    }

    private String normalizeTicker(String rawTicker) {
        if (rawTicker == null || rawTicker.isBlank()) {
            return null;
        }

        String ticker = rawTicker.trim().toUpperCase(Locale.ROOT);
        if (!TICKER_PATTERN.matcher(ticker).matches()) {
            throw new ObjectInvalidException("Invalid ticker: " + rawTicker);
        }
        return ticker;
    }

    private String normalizeSide(String rawSide) {
        if (rawSide == null || rawSide.isBlank()) {
            return null;
        }

        String side = rawSide.trim().toUpperCase(Locale.ROOT);
        if (!side.equals("BUY") && !side.equals("SELL")) {
            throw new ObjectInvalidException("Side must be BUY or SELL");
        }
        return side;
    }

    private <T> T required(T value, String field) {
        if (value == null) {
            throw new ObjectNotProcessedException("Analytics mapper returned null " + field);
        }
        return value;
    }
}