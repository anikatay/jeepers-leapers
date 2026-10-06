package com.neueda.leap.mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

import com.neueda.leap.model.UserPii;

import java.util.UUID;
import java.time.OffsetDateTime;

@Mapper
public interface UserPiiMapper {
    
    @Select("SELECT * FROM user_pii WHERE user_id = #{userId}")
    UserPii getUserPiiById(UUID userId);

    @Select("SELECT first_name FROM user_pii WHERE user_id = #{userId}")
    String getUserFirstNameById(UUID userId);

    @Select("SELECT date_of_birth FROM user_pii WHERE user_id = #{userId}")
    OffsetDateTime getUserDateOfBirthById(UUID userId);

    @Select("SELECT phone_number FROM user_pii WHERE user_id = #{userId}")
    String getUserPhoneNumberById(UUID userId);

    @Select("SELECT email FROM user_pii WHERE user_id = #{userId}")
    String getUserEmailById(UUID userId);

    @Select("SELECT user_id FROM user_pii WHERE email = #{email}")
    UUID getUserIdByEmail(String email);

    @Insert("INSERT INTO user_pii (user_id, first_name, last_name, date_of_birth, ssn, \"address\", phone_number, email) VALUES (#{userId}, #{firstName}, #{lastName}, #{dateOfBirth}, #{ssn}, #{address}, #{phoneNumber}, #{email})")
    int insertUserPii(UserPii userPii);

    @Update("UPDATE user_pii SET first_name = #{firstName}, last_name = #{lastName}, date_of_birth = #{dateOfBirth}, ssn = #{ssn}, \"address\" = #{address}, phone_number = #{phoneNumber}, email = #{email} WHERE user_id = #{userId}")
    int updateUserPii(UserPii userPii);

    @Delete("DELETE FROM user_pii WHERE user_id = #{userId}")
    int deleteUserPiiById(UUID userId);
}