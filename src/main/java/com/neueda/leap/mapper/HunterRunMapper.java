package com.neueda.leap.mapper;

import com.neueda.leap.model.HunterRunRecord;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

/** Callers check that the spec belongs to the user before touching its runs. */
@Mapper
public interface HunterRunMapper {

    @Insert("INSERT INTO hunter_runs (run_id, spec_id, spec_hash, ran_at, result, model, match_count, "
            + "prompt_tokens, completion_tokens, latency_ms) VALUES (#{runId}, #{specId}, #{specHash}, #{ranAt}, "
            + "CAST(#{resultJson} AS jsonb), #{model}, #{matchCount}, #{promptTokens}, #{completionTokens}, #{latencyMs})")
    int insert(HunterRunRecord record);

    @Select("SELECT run_id, spec_id, spec_hash, ran_at, result::text AS result_json, model, match_count, "
            + "prompt_tokens, completion_tokens, latency_ms FROM hunter_runs "
            + "WHERE spec_id = #{specId} ORDER BY ran_at DESC LIMIT 1")
    HunterRunRecord findLatest(@Param("specId") UUID specId);

    @Delete("DELETE FROM hunter_runs WHERE spec_id = #{specId} AND run_id NOT IN "
            + "(SELECT run_id FROM hunter_runs WHERE spec_id = #{specId} ORDER BY ran_at DESC LIMIT #{keep})")
    int deleteAllButLatest(@Param("specId") UUID specId, @Param("keep") int keep);
}
