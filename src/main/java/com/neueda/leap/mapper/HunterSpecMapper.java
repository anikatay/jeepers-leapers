package com.neueda.leap.mapper;

import com.neueda.leap.model.HunterSpecRecord;
import com.neueda.leap.model.HunterSpecSummary;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.UUID;

/** Every query is scoped to the owner, so another user's hunter is never found. */
@Mapper
public interface HunterSpecMapper {

    @Insert("INSERT INTO hunter_specs (spec_id, user_id, \"name\", prompt, spec, created_at, updated_at) "
            + "VALUES (#{specId}, #{userId}, #{name}, #{prompt}, CAST(#{specJson} AS jsonb), #{createdAt}, #{updatedAt})")
    int insert(HunterSpecRecord record);

    @Update("UPDATE hunter_specs SET \"name\" = #{name}, prompt = #{prompt}, spec = CAST(#{specJson} AS jsonb), "
            + "updated_at = #{updatedAt} WHERE spec_id = #{specId} AND user_id = #{userId}")
    int update(HunterSpecRecord record);

    @Delete("DELETE FROM hunter_specs WHERE spec_id = #{specId} AND user_id = #{userId}")
    int delete(@Param("specId") UUID specId, @Param("userId") UUID userId);

    @Select("SELECT spec_id, user_id, \"name\", prompt, spec::text AS spec_json, created_at, updated_at "
            + "FROM hunter_specs WHERE spec_id = #{specId} AND user_id = #{userId}")
    HunterSpecRecord findOwned(@Param("specId") UUID specId, @Param("userId") UUID userId);

    @Select("SELECT COUNT(*) FROM hunter_specs WHERE user_id = #{userId}")
    int countByUser(@Param("userId") UUID userId);

    @Select("SELECT s.spec_id, s.\"name\", s.updated_at, r.ran_at AS last_run_at, r.match_count AS last_match_count "
            + "FROM hunter_specs s "
            + "LEFT JOIN LATERAL (SELECT ran_at, match_count FROM hunter_runs "
            + "WHERE spec_id = s.spec_id ORDER BY ran_at DESC LIMIT 1) r ON TRUE "
            + "WHERE s.user_id = #{userId} ORDER BY s.updated_at DESC")
    List<HunterSpecSummary> findSummaries(@Param("userId") UUID userId);
}
