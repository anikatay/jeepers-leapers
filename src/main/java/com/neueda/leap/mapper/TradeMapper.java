package com.neueda.leap.mapper;

import java.util.UUID;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.neueda.leap.dto.response.TradeResponse;
import com.neueda.leap.model.Trade;

@Mapper
public interface TradeMapper {
    // insert new Trade
    @Insert("INSERT INTO trades (trade_id, account_id, instrument_id, side, quantity, execution_price, " +
            "settlement_value, settlement_currency, fx_rate, executed_at) " +
            "VALUES (#{tradeId}, #{accountId}, #{instrumentId}, #{side}, #{quantity}, #{executionPrice}, " +
            "#{settlementValue}, #{settlementCurrency}, #{fxRate}, #{executedAt})")
    int createTrade(Trade trade);

    // find trades all trades for a given account
    @Select("SELECT * FROM trades WHERE account_id = #{accountId}")
    List<Trade> findAllTradesForAccount(@Param("accountId")UUID accountId);

    // find all trades for an account by instrument
    @Select("SELECT * from trades WHERE account_id = #{accountId} AND instrument_id = #{instrumentId}")
    List<Trade> findAllTradesForAccountByInstrument(@Param("accountId")UUID accountId, @Param("instrumentId")UUID instrumentId);

    @Select(    "SELECT t.trade_id, t.account_id, t.instrument_id, i.ticker, i.name, i.currency AS instrument_currency, " +
        "t.side, t.quantity, t.execution_price, t.trade_value, " +
        "t.settlement_value, t.settlement_currency, t.fx_rate, t.executed_at " +
        "FROM trades t " +
        "JOIN instruments i ON t.instrument_id = i.instrument_id " +
        "WHERE t.account_id = #{accountId} " +
        "ORDER BY t.executed_at DESC " +
        "LIMIT #{limit} OFFSET #{offset}")
    List<TradeResponse> findAllTradesForAccountWithInstrument(@Param("accountId") UUID accountId, @Param("limit") int limit, @Param("offset") int offset);


    @Select(    "SELECT t.trade_id, t.account_id, t.instrument_id, i.ticker, i.name, i.currency AS instrument_currency, " +
        "t.side, t.quantity, t.execution_price, t.trade_value, " +
        "t.settlement_value, t.settlement_currency, t.fx_rate, t.executed_at " +
        "FROM trades t " +
        "JOIN instruments i ON t.instrument_id = i.instrument_id " +
        "WHERE t.account_id = #{accountId} AND t.instrument_id = #{instrumentId} " +
        "ORDER BY t.executed_at DESC")
    List<TradeResponse> findTradesByAccountAndInstrument(@Param("accountId") UUID accountId, @Param("instrumentId") UUID instrumentId);

}
