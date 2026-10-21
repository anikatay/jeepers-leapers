package com.neueda.leap.mapper.analytics;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.neueda.leap.model.analytics.projection.RefreshProjection;

public interface RefreshMapper {

    @Select("""
            SELECT etl_run_id,
                   CASE WHEN BOOL_OR(status = 'FAILED') THEN 'FAILED' ELSE 'SUCCESS' END AS status,
                   MIN("timestamp") AS started_at,
                   MAX("timestamp") AS last_event_at,
                   COUNT(*) AS event_count,
                   COALESCE(SUM(row_count), 0) AS row_count,
                   STRING_AGG(error_message, '; ' ORDER BY "timestamp")
                       FILTER (WHERE error_message IS NOT NULL) AS error_message
            FROM analytics.audit_trail
            WHERE etl_run_id = #{runId}
            GROUP BY etl_run_id
            """)
    RefreshProjection findByRunId(@Param("runId") String runId);

    @Select("""
            SELECT etl_run_id,
                   CASE WHEN BOOL_OR(status = 'FAILED') THEN 'FAILED' ELSE 'SUCCESS' END AS status,
                   MIN("timestamp") AS started_at,
                   MAX("timestamp") AS last_event_at,
                   COUNT(*) AS event_count,
                   COALESCE(SUM(row_count), 0) AS row_count,
                   STRING_AGG(error_message, '; ' ORDER BY "timestamp")
                       FILTER (WHERE error_message IS NOT NULL) AS error_message
            FROM analytics.audit_trail
            WHERE etl_run_id IS NOT NULL
            GROUP BY etl_run_id
            ORDER BY MAX("timestamp") DESC
            LIMIT 1
            """)
    RefreshProjection findLatest();
}