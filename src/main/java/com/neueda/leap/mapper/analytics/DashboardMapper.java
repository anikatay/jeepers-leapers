package com.neueda.leap.mapper.analytics;

import java.util.List;

import org.apache.ibatis.annotations.Select;

import com.neueda.leap.model.analytics.DashboardFlowQuery;
import com.neueda.leap.model.analytics.DashboardRange;
import com.neueda.leap.model.analytics.projection.DashboardFlowProjection;
import com.neueda.leap.model.analytics.projection.DashboardOutcomeProjection;
import com.neueda.leap.model.analytics.projection.DashboardSummaryProjection;

public interface DashboardMapper {

    @Select("""
            <script>
            SELECT COALESCE(ROUND(SUM(t.quantity * t.execution_price), 4), 0) AS total_volume,
                   COUNT(*) AS total_trades,
                   COUNT(DISTINCT t.account_id) AS active_accounts,
                   COALESCE(SUM(t.realized_pnl), 0) AS realized_pnl,
                   COALESCE(SUM(t.unrealized_pnl), 0) AS unrealized_pnl,
                   COALESCE(SUM(t.realized_pnl), 0) + COALESCE(SUM(t.unrealized_pnl), 0) AS total_pnl
            FROM analytics.fact_trades t
            <where>
                <if test="from != null">AND t.executed_date &gt;= #{from}</if>
                <if test="to != null">AND t.executed_date &lt;= #{to}</if>
            </where>
            </script>
            """)
    DashboardSummaryProjection findSummary(DashboardRange request);

    @Select("""
            <script>
            WITH bucketed AS (
                SELECT
                <choose>
                    <when test="bucket == 'week'">DATE_TRUNC('week', t.executed_date)::date</when>
                    <when test="bucket == 'month'">DATE_TRUNC('month', t.executed_date)::date</when>
                    <otherwise>t.executed_date</otherwise>
                </choose>
                AS bucket_date,
                t.side,
                t.quantity * t.execution_price AS trade_value
                FROM analytics.fact_trades t
                <where>
                    <if test="from != null">AND t.executed_date &gt;= #{from}</if>
                    <if test="to != null">AND t.executed_date &lt;= #{to}</if>
                </where>
            )
            SELECT bucket_date,
                   COALESCE(SUM(trade_value) FILTER (WHERE side = 'BUY'), 0) AS buy_volume,
                   COALESCE(SUM(trade_value) FILTER (WHERE side = 'SELL'), 0) AS sell_volume,
                   COALESCE(SUM(trade_value) FILTER (WHERE side = 'BUY'), 0)
                       - COALESCE(SUM(trade_value) FILTER (WHERE side = 'SELL'), 0) AS net_volume,
                   COUNT(*) AS trades
            FROM bucketed
            GROUP BY bucket_date
            ORDER BY bucket_date
            </script>
            """)
    List<DashboardFlowProjection> findFlow(DashboardFlowQuery request);

    @Select("""
            <script>
            SELECT t.account_id,
                   COUNT(*) AS trades,
                   COALESCE(ROUND(SUM(t.quantity * t.execution_price), 4), 0) AS notional,
                   COALESCE(SUM(t.realized_pnl), 0) + COALESCE(SUM(t.unrealized_pnl), 0) AS pnl
            FROM analytics.fact_trades t
            <where>
                <if test="from != null">AND t.executed_date &gt;= #{from}</if>
                <if test="to != null">AND t.executed_date &lt;= #{to}</if>
            </where>
            GROUP BY t.account_id
            ORDER BY pnl DESC, t.account_id
            </script>
            """)
    List<DashboardOutcomeProjection> findOutcomes(DashboardRange request);
}
