package com.neueda.leap.analysis;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the P&L the ETL pipeline writes to analytics.fact_trades. That database is optional:
 * when it is missing, mid-refresh or has no row for a trade, callers get an empty result.
 */
@Repository
public class AnalyticsPnlReader implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsPnlReader.class);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public record TradePnl(
            BigDecimal entryPrice,
            BigDecimal exitPrice,
            BigDecimal realizedPnl,
            BigDecimal unrealizedPnl,
            String pnlStatus,
            OffsetDateTime asOf) {}

    // Deliberately not a Spring bean: a second DataSource bean would switch off the
    // auto-configured one that MyBatis and schema.sql depend on.
    private final HikariDataSource dataSource;
    private final JdbcTemplate jdbc;

    public AnalyticsPnlReader(
            @Value("${analytics.datasource.url}") String url,
            @Value("${analytics.datasource.username}") String username,
            @Value("${analytics.datasource.password}") String password) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("analytics");
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setReadOnly(true);
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(3000);
        // The ETL pipeline creates this database, so the app must start without it.
        config.setInitializationFailTimeout(-1);
        this.dataSource = new HikariDataSource(config);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public Optional<TradePnl> findTradePnl(UUID tradeId) {
        try {
            List<TradePnl> rows = jdbc.query(
                    "SELECT entry_price, exit_price, realized_pnl, unrealized_pnl, pnl_status, etl_timestamp "
                            + "FROM analytics.fact_trades WHERE trade_id = ?",
                    (rs, rowNum) -> new TradePnl(
                            rs.getBigDecimal("entry_price"),
                            rs.getBigDecimal("exit_price"),
                            rs.getBigDecimal("realized_pnl"),
                            rs.getBigDecimal("unrealized_pnl"),
                            rs.getString("pnl_status"),
                            rs.getObject("etl_timestamp", OffsetDateTime.class)),
                    tradeId);
            return rows.stream().findFirst();
        } catch (DataAccessException e) {
            log.warn("Could not read P&L for trade {}: {}", tradeId, e.getMessage());
            return Optional.empty();
        }
    }

    /** Totals over every trade of the account; the win rate covers sells with a known cost basis. */
    public Optional<TradeAnalysisContext.TrackRecord> findTrackRecord(UUID accountId) {
        try {
            return jdbc.queryForObject(
                    "SELECT COUNT(*) AS trades, "
                            + "COALESCE(SUM(realized_pnl), 0) AS realized, "
                            + "COALESCE(SUM(unrealized_pnl), 0) AS unrealized, "
                            + "COUNT(*) FILTER (WHERE side = 'SELL' AND pnl_status IN ('MATCHED', 'PARTIAL')) AS costed_sells, "
                            + "COUNT(*) FILTER (WHERE side = 'SELL' AND pnl_status IN ('MATCHED', 'PARTIAL') "
                            + "AND realized_pnl > 0) AS winning_sells "
                            + "FROM analytics.fact_trades WHERE account_id = ?",
                    (rs, rowNum) -> {
                        if (rs.getLong("trades") == 0) {
                            return Optional.<TradeAnalysisContext.TrackRecord>empty();
                        }
                        long costedSells = rs.getLong("costed_sells");
                        BigDecimal winRatePct = costedSells == 0 ? null
                                : BigDecimal.valueOf(rs.getLong("winning_sells")).multiply(HUNDRED)
                                        .divide(BigDecimal.valueOf(costedSells), 2, RoundingMode.HALF_UP);
                        return Optional.of(new TradeAnalysisContext.TrackRecord(
                                rs.getBigDecimal("realized").setScale(2, RoundingMode.HALF_UP),
                                rs.getBigDecimal("unrealized").setScale(2, RoundingMode.HALF_UP),
                                winRatePct));
                    },
                    accountId);
        } catch (DataAccessException e) {
            log.warn("Could not read the track record for account {}: {}", accountId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void destroy() {
        dataSource.close();
    }
}
