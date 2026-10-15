package com.neueda.leap.analysis;

import com.neueda.leap.analysis.AnalyticsPnlReader.TradePnl;
import com.neueda.leap.analysis.TradeAnalysisContext.TrackRecord;
import com.neueda.leap.dto.response.TradeResponse;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.mapper.AccountMapper;
import com.neueda.leap.mapper.HoldingMapper;
import com.neueda.leap.mapper.InstrumentMapper;
import com.neueda.leap.mapper.TradeMapper;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Holding;
import com.neueda.leap.model.Instrument;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeAnalysisServiceTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID TRADE_ID = UUID.randomUUID();
    private static final UUID AAPL = UUID.randomUUID();
    private static final UUID MSFT = UUID.randomUUID();
    private static final OffsetDateTime PNL_AS_OF = OffsetDateTime.parse("2026-10-13T06:00:00Z");

    private final TradeMapper tradeMapper = mock(TradeMapper.class);
    private final InstrumentMapper instrumentMapper = mock(InstrumentMapper.class);
    private final AccountMapper accountMapper = mock(AccountMapper.class);
    private final HoldingMapper holdingMapper = mock(HoldingMapper.class);
    private final AnalyticsPnlReader pnlReader = mock(AnalyticsPnlReader.class);
    private final OpenAiTradeAnalyzer analyzer = mock(OpenAiTradeAnalyzer.class);

    private final TradeAnalysisService service = new TradeAnalysisService(
            tradeMapper, instrumentMapper, accountMapper, holdingMapper, pnlReader, analyzer, 2);

    @BeforeEach
    void setUp() {
        Account account = new Account();
        account.setAccountId(ACCOUNT_ID);
        account.setBalance(new BigDecimal("5000.0000"));
        when(accountMapper.findByAccountId(ACCOUNT_ID)).thenReturn(account);

        when(instrumentMapper.findAll()).thenReturn(List.of(
                instrument(AAPL, "AAPL", "229.0000"),
                instrument(MSFT, "MSFT", "430.2500")));

        // AAPL 10 x 229.00 = 2290.00, MSFT 20 x 430.25 = 8605.00, cash 5000.00: total 15895.00
        when(holdingMapper.getHoldingsByAccountId(ACCOUNT_ID)).thenReturn(List.of(
                new Holding(ACCOUNT_ID, AAPL, 10),
                new Holding(ACCOUNT_ID, MSFT, 20)));

        when(pnlReader.findTradePnl(any())).thenReturn(Optional.empty());
        when(pnlReader.findTrackRecord(any())).thenReturn(Optional.empty());
    }

    @Test
    void buildsContextForACostedSell() {
        TradeResponse sell = trade("SELL", 20, "229.0000", OffsetDateTime.now().minusDays(3).minusHours(1));
        TrackRecord trackRecord = new TrackRecord(bd("1840.00"), bd("-75.00"), bd("66.67"));
        when(tradeMapper.findTradeWithInstrumentById(TRADE_ID)).thenReturn(sell);
        when(tradeMapper.findRecentTradesForAccountWithInstrument(eq(ACCOUNT_ID), eq(10))).thenReturn(List.of(sell));
        when(pnlReader.findTradePnl(TRADE_ID)).thenReturn(Optional.of(new TradePnl(
                bd("198.5000"), bd("229.0000"), bd("610.0000"), bd("0.0000"), "MATCHED", PNL_AS_OF)));
        when(pnlReader.findTrackRecord(ACCOUNT_ID)).thenReturn(Optional.of(trackRecord));

        TradeAnalysisContext context = service.buildContext(TRADE_ID);

        assertEquals(new TradeAnalysisContext.Trade("AAPL", "AAPL Inc.", "NASDAQ", "SELL", bd("20"),
                bd("229.00"), bd("4580.00"), sell.getExecutedAt(), 3), context.trade());
        assertEquals(new TradeAnalysisContext.Outcome(bd("198.50"), bd("229.00"), bd("610.00"), null,
                bd("15.37"), "MATCHED", PNL_AS_OF), context.outcome());
        assertEquals(new TradeAnalysisContext.PriceNow(bd("229.00"), bd("0.00")), context.priceNow());
        assertEquals(new TradeAnalysisContext.Portfolio("USD", bd("5000.00"), bd("31.46"), bd("15895.00"), bd("28.81"),
                List.of(
                        new TradeAnalysisContext.Holding("MSFT", bd("20"), bd("8605.00"), bd("54.14")),
                        new TradeAnalysisContext.Holding("AAPL", bd("10"), bd("2290.00"), bd("14.41")))),
                context.portfolio());
        assertSame(trackRecord, context.trackRecord());
        assertEquals(List.of(new TradeAnalysisContext.RecentTrade(
                sell.getExecutedAt().toLocalDate(), "AAPL", "SELL", bd("20"), bd("229.00"))),
                context.recentTrades());
    }

    @Test
    void leavesOutcomeAndTrackRecordEmptyWhenAnalyticsHasNothing() {
        when(tradeMapper.findTradeWithInstrumentById(TRADE_ID))
                .thenReturn(trade("BUY", 5, "220.0000", OffsetDateTime.now()));

        TradeAnalysisContext context = service.buildContext(TRADE_ID);

        assertNull(context.outcome());
        assertNull(context.trackRecord());
        assertEquals(bd("4.09"), context.priceNow().movePctSinceExecution());
    }

    @Test
    void valuesAnOpenBuyAtTheCurrentPrice() {
        when(tradeMapper.findTradeWithInstrumentById(TRADE_ID))
                .thenReturn(trade("BUY", 5, "250.0000", OffsetDateTime.now()));
        when(pnlReader.findTradePnl(TRADE_ID)).thenReturn(Optional.of(new TradePnl(
                bd("250.0000"), null, bd("0.0000"), bd("-105.0000"), "UNMATCHED", PNL_AS_OF)));

        TradeAnalysisContext.Outcome outcome = service.buildContext(TRADE_ID).outcome();

        assertEquals(bd("-8.40"), outcome.returnPct());
        assertEquals(bd("-105.00"), outcome.unrealizedPnl());
        assertNull(outcome.realizedPnl());
        assertNull(outcome.exitPrice());
    }

    @Test
    void givesNoReturnForAFullySoldBuyOrAnUncostedSell() {
        when(tradeMapper.findTradeWithInstrumentById(TRADE_ID))
                .thenReturn(trade("BUY", 5, "250.0000", OffsetDateTime.now()));
        when(pnlReader.findTradePnl(TRADE_ID)).thenReturn(Optional.of(new TradePnl(
                bd("250.0000"), null, bd("0.0000"), bd("0.0000"), "MATCHED", PNL_AS_OF)));
        TradeAnalysisContext.Outcome fullySoldBuy = service.buildContext(TRADE_ID).outcome();
        assertNull(fullySoldBuy.returnPct());
        assertNull(fullySoldBuy.unrealizedPnl());

        when(tradeMapper.findTradeWithInstrumentById(TRADE_ID))
                .thenReturn(trade("SELL", 5, "250.0000", OffsetDateTime.now()));
        when(pnlReader.findTradePnl(TRADE_ID)).thenReturn(Optional.of(new TradePnl(
                null, bd("250.0000"), bd("0.0000"), bd("0.0000"), "UNMATCHED", PNL_AS_OF)));
        TradeAnalysisContext.Outcome uncostedSell = service.buildContext(TRADE_ID).outcome();
        assertNull(uncostedSell.returnPct());
        assertNull(uncostedSell.realizedPnl());
    }

    @Test
    void rejectsAnUnknownTrade() {
        assertThrows(ObjectNotFoundException.class, () -> service.buildContext(TRADE_ID));
    }

    @Test
    void analysesTheContextItBuilt() {
        when(tradeMapper.findTradeWithInstrumentById(TRADE_ID))
                .thenReturn(trade("BUY", 5, "220.0000", OffsetDateTime.now()));
        TradeAnalysisResult result = result("Too early to say.");
        when(analyzer.analyzeWithUsage(service.buildContext(TRADE_ID))).thenReturn(analysis(result));

        assertSame(result, service.analyze(TRADE_ID));
    }

    @Test
    void reusesTheAnalysisWhileTheContextIsUnchanged() {
        when(tradeMapper.findTradeWithInstrumentById(TRADE_ID))
                .thenReturn(trade("BUY", 5, "220.0000", OffsetDateTime.now()));
        when(analyzer.analyzeWithUsage(any())).thenReturn(analysis(result("First.")));

        service.analyze(TRADE_ID);
        service.analyze(TRADE_ID);

        verify(analyzer, times(1)).analyzeWithUsage(any());
    }

    @Test
    void analysesAgainWhenANewPnlRunChangesTheContext() {
        when(tradeMapper.findTradeWithInstrumentById(TRADE_ID))
                .thenReturn(trade("BUY", 5, "250.0000", OffsetDateTime.now()));
        when(pnlReader.findTradePnl(TRADE_ID)).thenReturn(Optional.of(new TradePnl(
                bd("250.0000"), null, bd("0.0000"), bd("-105.0000"), "UNMATCHED", PNL_AS_OF)));
        when(analyzer.analyzeWithUsage(any()))
                .thenReturn(analysis(result("Before the run.")), analysis(result("After the run.")));

        assertEquals("Before the run.", service.analyze(TRADE_ID).summary());
        when(pnlReader.findTradePnl(TRADE_ID)).thenReturn(Optional.of(new TradePnl(
                bd("250.0000"), null, bd("0.0000"), bd("-105.0000"), "UNMATCHED", PNL_AS_OF.plusMinutes(10))));

        assertEquals("After the run.", service.analyze(TRADE_ID).summary());
    }

    @Test
    void keepsOnlyTheMostRecentlyUsedAnalyses() {
        when(analyzer.analyzeWithUsage(any())).thenReturn(analysis(result("Any.")));
        TradeResponse first = trade("BUY", 1, "220.0000", OffsetDateTime.now());
        TradeResponse second = trade("BUY", 2, "220.0000", OffsetDateTime.now());
        TradeResponse third = trade("BUY", 3, "220.0000", OffsetDateTime.now());

        for (TradeResponse trade : List.of(first, second, third, first)) {
            when(tradeMapper.findTradeWithInstrumentById(TRADE_ID)).thenReturn(trade);
            service.analyze(TRADE_ID);
        }

        // The cache holds two entries, so the first trade was evicted by the third and analysed again.
        verify(analyzer, times(4)).analyzeWithUsage(any());
    }

    private static TradeAnalysisResult result(String summary) {
        return new TradeAnalysisResult(TradeAnalysisResult.Verdict.TOO_EARLY, summary, List.of(), List.of(), List.of());
    }

    private static OpenAiTradeAnalyzer.Analysis analysis(TradeAnalysisResult result) {
        return new OpenAiTradeAnalyzer.Analysis(result, "test-model", 800, 150, 1200);
    }

    private static TradeResponse trade(String side, int quantity, String price, OffsetDateTime executedAt) {
        BigDecimal executionPrice = bd(price);
        return new TradeResponse(TRADE_ID, ACCOUNT_ID, AAPL, "AAPL", "AAPL Inc.", side, quantity, executionPrice,
                executionPrice.multiply(BigDecimal.valueOf(quantity)), executedAt);
    }

    private static Instrument instrument(UUID id, String ticker, String currentPrice) {
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(id);
        instrument.setTicker(ticker);
        instrument.setExchange("NASDAQ");
        instrument.setCurrentPrice(bd(currentPrice));
        return instrument;
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
