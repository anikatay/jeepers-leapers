package com.neueda.leap.mapper.analytics;

import java.util.List;

import org.apache.ibatis.annotations.Select;

import com.neueda.leap.model.analytics.TradeSearchQuery;
import com.neueda.leap.model.analytics.projection.TradeProjection;

public interface TradeMapper {

    @Select("""
            <script>
            SELECT t.trade_id,
                   t.account_id,
                   t.instrument_id,
                   i.ticker,
                   i.name,
                   t.side,
                   t.quantity,
                   t.execution_price,
                   ROUND(t.quantity * t.execution_price, 4) AS trade_value,
                   t.executed_at,
                   COALESCE(t.realized_pnl, 0) + COALESCE(t.unrealized_pnl, 0) AS pnl
            FROM analytics.fact_trades t
            JOIN analytics.dim_instruments i ON i.instrument_id = t.instrument_id
            <where>
                <if test="from != null">AND t.executed_date &gt;= #{from}</if>
                <if test="to != null">AND t.executed_date &lt;= #{to}</if>
                <if test="ticker != null and ticker != ''">AND i.ticker = #{ticker}</if>
                <if test="accountId != null">AND t.account_id = #{accountId}</if>
                <if test="side != null and side != ''">AND t.side = #{side}</if>
            </where>
            ORDER BY t.executed_at DESC, t.trade_id
            <choose>
                <when test="size != null">LIMIT #{size}</when>
                <otherwise>LIMIT 50</otherwise>
            </choose>
            <choose>
                <when test="page != null and size != null">OFFSET (#{page} * #{size})</when>
                <otherwise>OFFSET 0</otherwise>
            </choose>
            </script>
            """)
    List<TradeProjection> findTrades(TradeSearchQuery request);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM analytics.fact_trades t
            JOIN analytics.dim_instruments i ON i.instrument_id = t.instrument_id
            <where>
                <if test="from != null">AND t.executed_date &gt;= #{from}</if>
                <if test="to != null">AND t.executed_date &lt;= #{to}</if>
                <if test="ticker != null and ticker != ''">AND i.ticker = #{ticker}</if>
                <if test="accountId != null">AND t.account_id = #{accountId}</if>
                <if test="side != null and side != ''">AND t.side = #{side}</if>
            </where>
            </script>
            """)
    long countTrades(TradeSearchQuery request);
}