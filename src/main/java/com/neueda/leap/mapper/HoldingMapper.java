package com.neueda.leap.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.util.List;
import java.util.UUID;

import com.neueda.leap.model.Holding;

@Mapper
public interface HoldingMapper {
    
    @Select("SELECT * FROM holdings")
    List<Holding> getAllHoldings();

    @Select("SELECT * FROM holdings WHERE account_id = #{accountId}")
    List<Holding> getHoldingsByAccountId(UUID accountId);

    @Select("SELECT instrument_id FROM holdings WHERE account_id = #{accountId}")
    List<UUID> getInstrumentsByAccountId(UUID accountId);

    @Select("SELECT * FROM holdings WHERE account_id = #{accountId} AND instrument_id = #{instrumentId}")
    Holding getHoldingByAccountIdAndInstrumentId(UUID accountId, UUID instrumentId);

    @Select("SELECT quantity FROM holdings WHERE account_id = #{accountId} AND instrument_id = #{instrumentId}")
    int getHoldingQuantity(UUID accountId, UUID instrumentId);

    @Insert("INSERT INTO holdings (account_id, instrument_id, quantity) VALUES (#{accountId}, #{instrumentId}, #{quantity})")
    int insertHolding(UUID accountId, UUID instrumentId, int quantity);

    @Update("UPDATE holdings SET quantity = #{quantity} WHERE account_id = #{accountId} AND instrument_id = #{instrumentId}")
    int updateHoldingQuantity(UUID accountId, UUID instrumentId, int quantity);

    @Delete("DELETE FROM holdings WHERE account_id = #{accountId} AND instrument_id = #{instrumentId}")
    int deleteHolding(UUID accountId, UUID instrumentId);

    @Delete("DELETE FROM holdings WHERE account_id = #{accountId}")
    int deleteHoldingsByAccountId(UUID accountId);

}