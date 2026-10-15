package com.neueda.leap.analysis;

import com.neueda.leap.analysis.TradeAnalysisContext.Holding;
import com.neueda.leap.analysis.TradeAnalysisContext.Outcome;
import com.neueda.leap.analysis.TradeAnalysisContext.Portfolio;
import com.neueda.leap.analysis.TradeAnalysisContext.PriceNow;
import com.neueda.leap.analysis.TradeAnalysisContext.RecentTrade;
import com.neueda.leap.analysis.TradeAnalysisContext.TrackRecord;
import com.neueda.leap.analysis.TradeAnalysisContext.Trade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Hand-built contexts standing in for what the backend will assemble from the databases. */
final class MockTradeContexts {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final OffsetDateTime PNL_AS_OF = OffsetDateTime.parse("2026-10-13T06:00:00Z");

    private MockTradeContexts() {}

    static Map<String, TradeAnalysisContext> scenarios() {
        Map<String, TradeAnalysisContext> scenarios = new LinkedHashMap<>();
        scenarios.put("profitable sell from a diversified portfolio", profitableSell());
        scenarios.put("concentrated buy sitting at a loss", concentratedLosingBuy());
        scenarios.put("fresh buy with no outcome yet", freshBuyWithoutOutcome());
        return scenarios;
    }

    static TradeAnalysisContext profitableSell() {
        BigDecimal entry = bd("198.50");
        BigDecimal exit = bd("229.00");
        BigDecimal tradeValue = bd("4580.00");
        return new TradeAnalysisContext(
                new Trade("AAPL", "Apple Inc.", "NASDAQ", "SELL", bd("20"), exit, tradeValue,
                        OffsetDateTime.parse("2026-09-29T14:30:00Z"), 14),
                new Outcome(entry, exit, bd("610.00"), null, pct(exit.subtract(entry), entry),
                        "MATCHED", PNL_AS_OF),
                new PriceNow(bd("229.00"), bd("0.00")),
                portfolio("12000.00", tradeValue,
                        position("AAPL", "30", "229.00"),
                        position("MSFT", "15", "430.25"),
                        position("GOOGL", "40", "176.50"),
                        position("AMZN", "25", "196.00")),
                new TrackRecord(bd("1840.00"), bd("1988.75"), bd("66.67")),
                List.of(
                        recent("2026-09-29", "AAPL", "SELL", "20", "229.00"),
                        recent("2026-09-10", "AMZN", "BUY", "25", "188.00"),
                        recent("2026-08-18", "GOOGL", "BUY", "40", "165.25"),
                        recent("2026-07-02", "AAPL", "BUY", "50", "198.50"),
                        recent("2026-06-15", "MSFT", "BUY", "15", "402.00")));
    }

    static TradeAnalysisContext concentratedLosingBuy() {
        BigDecimal entry = bd("285.00");
        BigDecimal current = bd("254.75");
        BigDecimal tradeValue = bd("17100.00");
        BigDecimal movePct = pct(current.subtract(entry), entry);
        return new TradeAnalysisContext(
                new Trade("TSLA", "Tesla Inc.", "NASDAQ", "BUY", bd("60"), entry, tradeValue,
                        OffsetDateTime.parse("2026-09-22T15:05:00Z"), 21),
                new Outcome(entry, null, null, bd("-1815.00"), movePct, "UNMATCHED", PNL_AS_OF),
                new PriceNow(current, movePct),
                portfolio("1200.00", tradeValue,
                        position("TSLA", "60", "254.75"),
                        position("NVDA", "30", "138.25"),
                        position("META", "8", "575.50")),
                new TrackRecord(bd("-420.00"), bd("-1473.50"), bd("33.33")),
                List.of(
                        recent("2026-09-22", "TSLA", "BUY", "60", "285.00"),
                        recent("2026-09-22", "MSFT", "SELL", "20", "428.00"),
                        recent("2026-09-21", "GOOGL", "SELL", "45", "175.00"),
                        recent("2026-08-30", "NVDA", "BUY", "30", "131.00"),
                        recent("2026-08-05", "META", "BUY", "8", "560.00")));
    }

    /** A trade newer than the last ETL run: no row in analytics.fact_trades, so no outcome. */
    static TradeAnalysisContext freshBuyWithoutOutcome() {
        BigDecimal price = bd("430.25");
        BigDecimal tradeValue = bd("2151.25");
        return new TradeAnalysisContext(
                new Trade("MSFT", "Microsoft Corp.", "NASDAQ", "BUY", bd("5"), price, tradeValue,
                        OffsetDateTime.parse("2026-10-13T15:55:00Z"), 0),
                null,
                new PriceNow(price, bd("0.00")),
                portfolio("18000.00", tradeValue,
                        position("MSFT", "5", "430.25"),
                        position("AAPL", "20", "229.00"),
                        position("GOOGL", "30", "176.50"),
                        position("AMZN", "20", "196.00")),
                new TrackRecord(bd("350.00"), bd("755.00"), bd("60.00")),
                List.of(
                        recent("2026-10-13", "MSFT", "BUY", "5", "430.25"),
                        recent("2026-09-18", "AMZN", "BUY", "20", "190.00"),
                        recent("2026-08-27", "GOOGL", "BUY", "30", "168.00"),
                        recent("2026-08-03", "AAPL", "BUY", "20", "210.00"),
                        recent("2026-07-20", "NVDA", "SELL", "10", "142.00")));
    }

    private record Position(String ticker, BigDecimal quantity, BigDecimal marketValue) {}

    private static Position position(String ticker, String quantity, String price) {
        BigDecimal qty = bd(quantity);
        return new Position(ticker, qty, qty.multiply(bd(price)).setScale(2, RoundingMode.HALF_UP));
    }

    private static Portfolio portfolio(String cash, BigDecimal tradeValue, Position... positions) {
        BigDecimal total = bd(cash);
        for (Position position : positions) {
            total = total.add(position.marketValue());
        }
        List<Holding> holdings = new ArrayList<>();
        for (Position position : positions) {
            holdings.add(new Holding(position.ticker(), position.quantity(), position.marketValue(),
                    pct(position.marketValue(), total)));
        }
        return new Portfolio("USD", bd(cash), pct(bd(cash), total), total, pct(tradeValue, total), holdings);
    }

    private static RecentTrade recent(String date, String ticker, String side, String quantity, String price) {
        return new RecentTrade(LocalDate.parse(date), ticker, side, bd(quantity), bd(price));
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        return part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
