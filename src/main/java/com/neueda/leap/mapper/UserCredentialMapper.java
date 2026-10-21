package com.neueda.leap.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import java.util.UUID;

@Mapper
public interface UserCredentialMapper {

    @Select("SELECT password_hash FROM user_credentials WHERE user_id = #{userId}")
    String getPasswordHashByUserId(UUID userId);

    @Insert("INSERT INTO user_credentials (user_id, password_hash) VALUES (#{userId}, #{passwordHash})")
    int insertUserCredential(UUID userId, String passwordHash);

    @Update("UPDATE user_credentials SET password_hash = #{passwordHash} WHERE user_id = #{userId}")
    int updateUserCredential(UUID userId, String passwordHash);

    @Delete("DELETE FROM user_credentials WHERE user_id = #{userId}")
    int deleteUserCredential(UUID userId);

}