package com.neueda.leap.mapper;

import com.neueda.leap.dto.response.AdminDashboardRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminDashboardMapper {

    @Select("""
            SELECT u.user_id,
                   p.first_name,
                   p.last_name,
                   p.email,
                   u.role AS user_role,
                   u.status AS user_status,
                   a.account_id,
                   a.currency,
                   a.balance,
                   a.status AS account_status,
                   a.freeze_reason,
                   a.created_at,
                   COUNT(t.trade_id) AS trade_count,
                   COALESCE((
                       SELECT SUM(h.quantity * i.current_price)
                       FROM holdings h
                       JOIN instruments i ON i.instrument_id = h.instrument_id
                       WHERE h.account_id = a.account_id
                   ), 0) AS portfolio_value,
                   COALESCE((
                       SELECT SUM(CASE WHEN t2.side = 'SELL' THEN t2.trade_value ELSE -t2.trade_value END)
                       FROM trades t2
                       WHERE t2.account_id = a.account_id
                   ), 0) + COALESCE((
                       SELECT SUM(h2.quantity * i2.current_price)
                       FROM holdings h2
                       JOIN instruments i2 ON i2.instrument_id = h2.instrument_id
                       WHERE h2.account_id = a.account_id
                   ), 0) AS all_time_profit
            FROM users u
            LEFT JOIN user_pii p ON p.user_id = u.user_id
            JOIN accounts a ON a.user_id = u.user_id
            LEFT JOIN trades t ON t.account_id = a.account_id
            WHERE (
                COALESCE(CAST(#{search} AS TEXT), '') = ''
                OR CAST(u.user_id AS TEXT) ILIKE CONCAT('%', CAST(#{search} AS TEXT), '%')
                OR CAST(a.account_id AS TEXT) ILIKE CONCAT('%', CAST(#{search} AS TEXT), '%')
                OR p.email ILIKE CONCAT('%', CAST(#{search} AS TEXT), '%')
                OR p.first_name ILIKE CONCAT('%', CAST(#{search} AS TEXT), '%')
                OR p.last_name ILIKE CONCAT('%', CAST(#{search} AS TEXT), '%')
                OR EXISTS (
                    SELECT 1
                    FROM trades ts
                    JOIN instruments ins ON ins.instrument_id = ts.instrument_id
                    WHERE ts.account_id = a.account_id
                      AND (
                          CAST(ts.trade_id AS TEXT) ILIKE CONCAT('%', CAST(#{search} AS TEXT), '%')
                          OR ins.ticker ILIKE CONCAT('%', CAST(#{search} AS TEXT), '%')
                      )
                )
            )
            GROUP BY u.user_id, p.first_name, p.last_name, p.email, u.role, u.status,
                     a.account_id, a.currency, a.balance, a.status, a.freeze_reason, a.created_at
            ORDER BY a.created_at DESC, a.account_id
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<AdminDashboardRow> findDashboardRows(@Param("search") String search,
            @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT COUNT(DISTINCT a.account_id)
            FROM users u
            LEFT JOIN user_pii p ON p.user_id = u.user_id
            JOIN accounts a ON a.user_id = u.user_id
            WHERE (
                COALESCE(CAST(#{search} AS TEXT), '') = ''
                OR CAST(u.user_id AS TEXT) ILIKE CONCAT('%', CAST(#{search} AS TEXT), '%')
                OR CAST(a.account_id AS TEXT) ILIKE CONCAT('%', CAST(#{search} AS TEXT), '%')
                OR p.email ILIKE CONCAT('%', CAST(#{search} AS TEXT), '%')
                OR p.first_name ILIKE CONCAT('%', CAST(#{search} AS TEXT), '%')
                OR p.last_name ILIKE CONCAT('%', CAST(#{search} AS TEXT), '%')
                OR EXISTS (
                    SELECT 1
                    FROM trades ts
                    JOIN instruments ins ON ins.instrument_id = ts.instrument_id
                    WHERE ts.account_id = a.account_id
                      AND (
                          CAST(ts.trade_id AS TEXT) ILIKE CONCAT('%', CAST(#{search} AS TEXT), '%')
                          OR ins.ticker ILIKE CONCAT('%', CAST(#{search} AS TEXT), '%')
                      )
                )
            )
            """)
    long countDashboardRows(@Param("search") String search);
}
