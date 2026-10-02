package com.neueda.leap.mapper;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import org.apache.ibatis.annotations.Mapper;

import com.neueda.leap.model.User;
import com.neueda.leap.model.UserPii;

import java.util.UUID;
import java.util.List;
import java.time.OffsetDateTime;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM users WHERE user_id = #{userId}")
    User getUserById(UUID userId);

    @Select("SELECT * FROM users")
    List<User> getAllUsers();

    @Insert("INSERT INTO users (user_id, role, status) VALUES (#{userId}, #{role}, #{status})")
    void insertUser(UUID userId, String role, String status);

    @Update("UPDATE users SET status = #{status} WHERE user_id = #{userId}")
    void updateUserStatus(UUID userId, String status);

    @Delete("DELETE FROM users WHERE user_id = #{userId}")
    void deleteUserById(UUID userId);

}