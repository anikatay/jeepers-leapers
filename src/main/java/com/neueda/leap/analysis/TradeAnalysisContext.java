package com.neueda.leap.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Everything the model is told about one trade. All figures are computed by the caller;
 * percentages are in percent units (12.5 means 12.5%).
 */
public record TradeAnalysisContext(
        Trade trade,
        Outcome outcome,
        PriceNow priceNow,
        Portfolio portfolio,
        TrackRecord trackRecord,
        List<RecentTrade> recentTrades) {

    public record Trade(
            String ticker,
            String name,
            String exchange,
            String side,
            BigDecimal quantity,
            BigDecimal executionPrice,
            BigDecimal tradeValue,
            OffsetDateTime executedAt,
            long daysAgo) {}

    /**
     * The P&L columns of analytics.fact_trades; null when the trade has no row there yet. A field is
     * null when it does not apply to the side (realized for buys, unrealized for sells) or is unknown.
     */
    public record Outcome(
            BigDecimal entryPrice,
            BigDecimal exitPrice,
            BigDecimal realizedPnl,
            BigDecimal unrealizedPnl,
            BigDecimal returnPct,
            String pnlStatus,
            OffsetDateTime asOf) {}

    public record PriceNow(
            BigDecimal currentPrice,
            BigDecimal movePctSinceExecution) {}

    /** The portfolio as it stands now, after the trade. */
    public record Portfolio(
            String currency,
            BigDecimal cash,
            BigDecimal cashPct,
            BigDecimal totalValue,
            BigDecimal tradeValuePctOfTotal,
            List<Holding> holdings) {}

    public record Holding(
            String ticker,
            BigDecimal quantity,
            BigDecimal marketValue,
            BigDecimal weightPct) {}

    public record TrackRecord(
            BigDecimal totalRealizedPnl,
            BigDecimal totalUnrealizedPnl,
            BigDecimal winRatePct) {}

    public record RecentTrade(
            LocalDate date,
            String ticker,
            String side,
            BigDecimal quantity,
            BigDecimal price) {}
}
