package com.neueda.leap.mapper.analytics;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.neueda.leap.model.analytics.projection.InstrumentProjection;

public interface InstrumentMapper {

    @Select("""
            SELECT instrument_id,
                   ticker,
                   name,
                   exchange_id,
                   current_price,
                   updated_at
            FROM analytics.dim_instruments
            ORDER BY ticker
            """)
        List<InstrumentProjection> findAll();

    @Select("""
            SELECT instrument_id,
                   ticker,
                   name,
                   exchange_id,
                   current_price,
                   updated_at
            FROM analytics.dim_instruments
            WHERE ticker = #{ticker}
            """)
        InstrumentProjection findByTicker(@Param("ticker") String ticker);
}
