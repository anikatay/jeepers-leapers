package com.neueda.leap.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface UserCredentialMapper {

    @Select("SELECT password_hash FROM user_credentials WHERE user_id = #{userId}")
    String getPasswordHashByUserId(String userId);

    @Insert("INSERT INTO user_credentials (user_id, password_hash) VALUES (#{userId}, #{passwordHash})")
    int insertUserCredential(String userId, String passwordHash);

    @Update("UPDATE user_credentials SET password_hash = #{passwordHash} WHERE user_id = #{userId}")
    int updateUserCredential(String userId, String passwordHash);

    @Delete("DELETE FROM user_credentials WHERE user_id = #{userId}")
    int deleteUserCredential(String userId);

}