package com.neueda.leap.mapper;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.neueda.leap.model.Instrument;

@Mapper
public interface InstrumentMapper extends OltpMapper {
    // Add new Instrument 
    @Insert ("INSERT INTO instruments (instrument_id, ticker, name, exchange_id, currency, price_scale, current_price, updated_at) " +
        "VALUES (#{instrumentId}, #{ticker}, #{name}, #{exchangeId}, #{currency}, #{priceScale}, #{currentPrice}, #{updatedAt})")
    int addInstrument(Instrument instrument);

    @Insert("""
            INSERT INTO instruments (
                instrument_id,
                ticker,
                name,
                exchange_id,
                currency,
                price_scale,
                current_price,
                updated_at
            )
            VALUES (
                #{instrumentId},
                #{ticker},
                #{name},
                #{exchangeId},
                #{currency},
                #{priceScale},
                #{currentPrice},
                #{updatedAt}
            )
            ON CONFLICT (ticker) DO NOTHING
            """)
    int insertIfAbsent(
            @Param("instrumentId") UUID instrumentId,
            @Param("ticker") String ticker,
            @Param("name") String name,
            @Param("exchangeId") String exchangeId,
            @Param("currency") String currency,
            @Param("priceScale") java.math.BigDecimal priceScale,
            @Param("currentPrice") java.math.BigDecimal currentPrice,
            @Param("updatedAt") java.time.OffsetDateTime updatedAt);
    
    // Read a single instrument
    @Select("SELECT * FROM instruments WHERE instrument_id = #{instrumentId}")
    Instrument findById(@Param("instrumentId") UUID instrumentId);

    @Select("SELECT * FROM instruments WHERE ticker = #{ticker}")
    Instrument findByTicker(@Param("ticker") String ticker);

    @Select("SELECT * FROM instruments")
    List<Instrument> findAll();

    @Update("UPDATE instruments SET current_price = #{currentPrice}, updated_at = #{updatedAt} " +
            "WHERE instrument_id = #{instrumentId}")
    int updatePrice(Instrument instrument);

    @Delete("DELETE FROM instruments WHERE instrument_id = #{instrumentId}")
    int deleteInstrument(@Param("instrumentId") UUID instrumentId);

}
