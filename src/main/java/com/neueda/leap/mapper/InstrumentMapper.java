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
public interface InstrumentMapper {
    // Add new Instrument 
    @Insert ("INSERT INTO instruments (instrument_id, ticker, name, exchange_id, current_price, updated_at) " +
        "VALUES (#{instrumentId}, #{ticker}, #{name}, #{exchange}, #{currentPrice}, #{updatedAt})")
    void addInstrument(Instrument instrument);
    
    // Read a single instrument
    @Select("SELECT * FROM instruments WHERE instrument_id = #{instrumentId}")
    Instrument findById(@Param("instrumentId") UUID instrumentId);

    @Select("SELECT * FROM instruments WHERE ticker = #{ticker}")
    Instrument findByTicker(@Param("ticker") String ticker);

    @Select("SELECT * FROM instruments")
    List<Instrument> findAll();

    @Update("UPDATE instruments SET current_price = #{currentPrice}, updated_at = #{updatedAt} " +
            "WHERE instrument_id = #{instrumentId}")
    void updatePrice(Instrument instrument);

    @Delete("DELETE FROM instruments WHERE instrument_id = #{instrumentId}")
    void deleteInstrument(@Param("instrumentId") UUID instrumentId);

}
