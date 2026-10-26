package com.neueda.leap.mapper;

import com.neueda.leap.model.WatchlistItemRecord;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface WatchlistMapper {

    /** 1 when added, 0 when the ticker was already on the list. */
    @Insert("INSERT INTO watchlist_items (user_id, ticker, added_at, source_spec_id) "
            + "VALUES (#{userId}, #{ticker}, #{addedAt}, #{sourceSpecId}) ON CONFLICT (user_id, ticker) DO NOTHING")
    int insert(@Param("userId") UUID userId, @Param("ticker") String ticker,
            @Param("addedAt") OffsetDateTime addedAt, @Param("sourceSpecId") UUID sourceSpecId);

    @Delete("DELETE FROM watchlist_items WHERE user_id = #{userId} AND ticker = #{ticker}")
    int delete(@Param("userId") UUID userId, @Param("ticker") String ticker);

    @Select("SELECT EXISTS (SELECT 1 FROM watchlist_items WHERE user_id = #{userId} AND ticker = #{ticker})")
    boolean exists(@Param("userId") UUID userId, @Param("ticker") String ticker);

    @Select("SELECT COUNT(*) FROM watchlist_items WHERE user_id = #{userId}")
    int countByUser(@Param("userId") UUID userId);

    @Select("SELECT w.ticker, w.added_at, w.source_spec_id, s.\"name\" AS source_spec_name "
            + "FROM watchlist_items w LEFT JOIN hunter_specs s ON s.spec_id = w.source_spec_id "
            + "WHERE w.user_id = #{userId} ORDER BY w.added_at DESC")
    List<WatchlistItemRecord> findAll(@Param("userId") UUID userId);

    @Select("SELECT ticker FROM watchlist_items WHERE user_id = #{userId}")
    List<String> findTickers(@Param("userId") UUID userId);
}
