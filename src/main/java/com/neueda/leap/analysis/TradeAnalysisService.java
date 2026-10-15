package com.neueda.leap.analysis;

import com.neueda.leap.analysis.AnalyticsPnlReader.TradePnl;
import com.neueda.leap.analysis.TradeAnalysisContext.Holding;
import com.neueda.leap.analysis.TradeAnalysisContext.Outcome;
import com.neueda.leap.analysis.TradeAnalysisContext.Portfolio;
import com.neueda.leap.analysis.TradeAnalysisContext.PriceNow;
import com.neueda.leap.analysis.TradeAnalysisContext.RecentTrade;
import com.neueda.leap.analysis.TradeAnalysisContext.Trade;
import com.neueda.leap.dto.response.TradeResponse;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.mapper.AccountMapper;
import com.neueda.leap.mapper.HoldingMapper;
import com.neueda.leap.mapper.InstrumentMapper;
import com.neueda.leap.mapper.TradeMapper;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Instrument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Assembles the context for one trade from the databases and asks the model to judge it. */
@Service
public class TradeAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(TradeAnalysisService.class);
    private static final int RECENT_TRADES = 10;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final TradeMapper tradeMapper;
    private final InstrumentMapper instrumentMapper;
    private final AccountMapper accountMapper;
    private final HoldingMapper holdingMapper;
    private final AnalyticsPnlReader pnlReader;
    private final OpenAiTradeAnalyzer analyzer;
    // Keyed by everything the model is told, so any change (new P&L run, price, holdings) misses.
    private final Map<TradeAnalysisContext, TradeAnalysisResult> cache;

    public TradeAnalysisService(TradeMapper tradeMapper, InstrumentMapper instrumentMapper,
            AccountMapper accountMapper, HoldingMapper holdingMapper,
            AnalyticsPnlReader pnlReader, OpenAiTradeAnalyzer analyzer,
            @Value("${analysis.cache.max-entries}") int cacheMaxEntries) {
        this.tradeMapper = tradeMapper;
        this.instrumentMapper = instrumentMapper;
        this.accountMapper = accountMapper;
        this.holdingMapper = holdingMapper;
        this.pnlReader = pnlReader;
        this.analyzer = analyzer;
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<TradeAnalysisContext, TradeAnalysisResult> eldest) {
                return size() > cacheMaxEntries;
            }
        });
    }

    public TradeAnalysisResult analyze(UUID tradeId) {
        TradeAnalysisContext context = buildContext(tradeId);
        TradeAnalysisResult cached = cache.get(context);
        if (cached != null) {
            log.info("analysis tradeId={} cache=hit verdict={}", tradeId, cached.verdict());
            return cached;
        }
        OpenAiTradeAnalyzer.Analysis analysis = analyzer.analyzeWithUsage(context);
        cache.put(context, analysis.result());
        log.info("analysis tradeId={} cache=miss verdict={} model={} promptTokens={} completionTokens={} latencyMs={}",
                tradeId, analysis.result().verdict(), analysis.model(), analysis.promptTokens(),
                analysis.completionTokens(), analysis.latencyMs());
        return analysis.result();
    }

    public TradeAnalysisContext buildContext(UUID tradeId) {
        TradeResponse trade = tradeMapper.findTradeWithInstrumentById(tradeId);
        if (trade == null) {
            throw new ObjectNotFoundException("Trade not found: " + tradeId);
        }
        Map<UUID, Instrument> instruments = instrumentMapper.findAll().stream()
                .collect(Collectors.toMap(Instrument::getInstrumentId, Function.identity()));
        Instrument instrument = instruments.get(trade.getInstrumentId());
        Account account = accountMapper.findByAccountId(trade.getAccountId());

        BigDecimal quantity = BigDecimal.valueOf(trade.getQuantity());
        BigDecimal executionPrice = trade.getExecutionPrice();
        BigDecimal currentPrice = instrument.getCurrentPrice();
        BigDecimal tradeValue = money(trade.getTradeValue());

        return new TradeAnalysisContext(
                new Trade(trade.getTicker(), trade.getInstrumentName(), instrument.getExchange(), trade.getSide(),
                        quantity, price(executionPrice), tradeValue, trade.getExecutedAt(),
                        ChronoUnit.DAYS.between(trade.getExecutedAt(), OffsetDateTime.now())),
                pnlReader.findTradePnl(tradeId)
                        .map(pnl -> outcome(pnl, trade.getSide(), currentPrice))
                        .orElse(null),
                new PriceNow(price(currentPrice), pct(currentPrice.subtract(executionPrice), executionPrice)),
                portfolio(account, tradeValue, instruments),
                pnlReader.findTrackRecord(trade.getAccountId()).orElse(null),
                tradeMapper.findRecentTradesForAccountWithInstrument(trade.getAccountId(), RECENT_TRADES).stream()
                        .map(recent -> new RecentTrade(recent.getExecutedAt().toLocalDate(), recent.getTicker(),
                                recent.getSide(), BigDecimal.valueOf(recent.getQuantity()),
                                price(recent.getExecutionPrice())))
                        .toList());
    }

    private static Outcome outcome(TradePnl pnl, String side, BigDecimal currentPrice) {
        boolean sell = "SELL".equals(side);
        BigDecimal entry = pnl.entryPrice();
        BigDecimal exit = sell ? pnl.exitPrice() : currentPrice;
        // A fully sold buy has nothing left to value; its result sits on the sells that closed it.
        boolean nothingToValue = entry == null || exit == null || (!sell && "MATCHED".equals(pnl.pnlStatus()));
        return new Outcome(price(entry), price(pnl.exitPrice()), money(pnl.realizedPnl()),
                money(pnl.unrealizedPnl()), nothingToValue ? null : pct(exit.subtract(entry), entry),
                pnl.pnlStatus(), pnl.asOf());
    }

    private Portfolio portfolio(Account account, BigDecimal tradeValue, Map<UUID, Instrument> instruments) {
        record Position(String ticker, BigDecimal quantity, BigDecimal marketValue) {}

        List<Position> positions = holdingMapper.getHoldingsByAccountId(account.getAccountId()).stream()
                .map(holding -> {
                    Instrument held = instruments.get(holding.getInstrumentId());
                    BigDecimal quantity = BigDecimal.valueOf(holding.getQuantity());
                    return new Position(held.getTicker(), quantity, money(held.getCurrentPrice().multiply(quantity)));
                })
                .sorted(Comparator.comparing(Position::marketValue).reversed())
                .toList();

        BigDecimal cash = money(account.getBalance());
        BigDecimal totalValue = positions.stream().map(Position::marketValue).reduce(cash, BigDecimal::add);

        return new Portfolio(account.getCurrency(), cash, totalValue, pct(tradeValue, totalValue),
                positions.stream()
                        .map(position -> new Holding(position.ticker(), position.quantity(),
                                position.marketValue(), pct(position.marketValue(), totalValue)))
                        .toList());
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? null : part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Prices are stored to four places; drop trailing zeros but keep at least cents. */
    private static BigDecimal price(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 2 ? stripped.setScale(2) : stripped;
    }
}
