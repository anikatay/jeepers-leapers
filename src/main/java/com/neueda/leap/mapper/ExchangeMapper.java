package com.neueda.leap.mapper;

import com.neueda.leap.model.Exchange;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ExchangeMapper {

    @Select("SELECT exchange_id AS exchangeId, name, region, timezone, currency FROM exchanges")
    List<Exchange> findAll();

    @Select("SELECT exchange_id AS exchangeId, name, region, timezone, currency FROM exchanges WHERE exchange_id = #{exchangeId}")
    Optional<Exchange> findById(String exchangeId);

    @Insert("INSERT INTO exchanges (exchange_id, name, region, timezone, currency) " +
            "VALUES (#{exchangeId}, #{name}, #{region}, #{timezone}, #{currency})")
    int insert(Exchange exchange);

    @Update("UPDATE exchanges SET name = #{name}, region = #{region}, timezone = #{timezone}, currency = #{currency} " +
            "WHERE exchange_id = #{exchangeId}")
    int update(Exchange exchange);

    @Delete("DELETE FROM exchanges WHERE exchange_id = #{exchangeId}")
    int deleteById(String exchangeId);
}
