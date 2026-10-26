package com.neueda.leap.mapper;

import com.neueda.leap.dto.request.TradeFilter;

import java.util.Map;

public final class TradeMapperSqlProvider {
    private TradeMapperSqlProvider() {
    }

    public static String findTradesForAccountPage(Map<String, Object> parameters) {
        TradeFilter filter = (TradeFilter) parameters.get("filter");
        StringBuilder sql = new StringBuilder(baseQuery());
        appendFilters(sql, filter);
        sql.append(" ORDER BY ");
        if (filter != null && "oldest".equalsIgnoreCase(filter.sortOrder())) {
            sql.append("t.executed_at ASC, t.trade_id ASC");
        } else {
            sql.append("t.executed_at DESC, t.trade_id DESC");
        }
        sql.append(" LIMIT #{limit} OFFSET #{offset}");
        return sql.toString();
    }

    public static String countTradesForAccountPage(Map<String, Object> parameters) {
        TradeFilter filter = (TradeFilter) parameters.get("filter");
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM trades t JOIN instruments i ON t.instrument_id = i.instrument_id "
                        + "WHERE t.account_id = #{accountId}");
        appendFilters(sql, filter);
        return sql.toString();
    }

    private static String baseQuery() {
        return "SELECT t.trade_id, t.account_id, t.instrument_id, i.ticker, i.name, "
                + "i.currency AS instrument_currency, t.side, t.quantity, t.execution_price, t.trade_value, "
                + "t.settlement_value, t.settlement_currency, t.fx_rate, t.executed_at "
                + "FROM trades t JOIN instruments i ON t.instrument_id = i.instrument_id "
                + "WHERE t.account_id = #{accountId}";
    }

    private static void appendFilters(StringBuilder sql, TradeFilter filter) {
        if (filter == null) {
            return;
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            sql.append(" AND (LOWER(CAST(t.trade_id AS TEXT)) LIKE LOWER(CONCAT('%', #{filter.search}, '%'))")
                    .append(" OR LOWER(i.ticker) LIKE LOWER(CONCAT('%', #{filter.search}, '%'))")
                    .append(" OR LOWER(i.name) LIKE LOWER(CONCAT('%', #{filter.search}, '%')))");
        }
        if (filter.side() != null) {
            sql.append(" AND t.side = #{filter.side}");
        }
        if (filter.currency() != null) {
            sql.append(" AND i.currency = #{filter.currency}");
        }
        if (filter.fromDate() != null) {
            sql.append(" AND t.executed_at >= CAST(#{filter.fromDate} AS date)");
        }
        if (filter.toDate() != null) {
            sql.append(" AND t.executed_at < CAST(#{filter.toDate} AS date) + INTERVAL '1 day'");
        }
        if (filter.minQuantity() != null) {
            sql.append(" AND t.quantity >= #{filter.minQuantity}");
        }
        if (filter.maxQuantity() != null) {
            sql.append(" AND t.quantity <= #{filter.maxQuantity}");
        }
        if (filter.minPrice() != null) {
            sql.append(" AND t.execution_price >= #{filter.minPrice}");
        }
        if (filter.maxPrice() != null) {
            sql.append(" AND t.execution_price <= #{filter.maxPrice}");
        }
        if (filter.minTotal() != null) {
            sql.append(" AND t.trade_value >= #{filter.minTotal}");
        }
        if (filter.maxTotal() != null) {
            sql.append(" AND t.trade_value <= #{filter.maxTotal}");
        }
    }
}