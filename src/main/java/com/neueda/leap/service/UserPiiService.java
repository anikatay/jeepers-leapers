package com.neueda.leap.service;

import com.neueda.leap.service.UserPiiMapper;
import java.util.UUID;

public class UserPiiService {
    private UserPiiMapper userPiiMapper;

    public UserPiiService(UserPiiMapper userPiiMapper) {
        this.userPiiMapper = userPiiMapper;
    }

    public UserPii getUserPiiById(UUID id) {
        return userPiiMapper.getUserPiiById(id);
    }

    public String getUserFirstNameById(UUID id) {
        return userPiiMapper.getUserFirstNameById(id);
    }

    public String getUserLastNameById(UUID id) {
        return userPiiMapper.getUserLastNameById(id);
    }

    public String getUserEmailById(UUID id) {
        return userPiiMapper.getUserEmailById(id);
    }

    public String getUserPhoneById(UUID id) {
        String phoneNumber = userPiiMapper.getUserPhoneById(id);
        // TODO: regex check
        return phoneNumber;
    }

    public UserPii insertUserPii(UserPii userPii) {
        // TODO: validate userPii before insertion
        int insertFlag = userPiiMapper.insertUserPii(userPii);
        return insertFlag > 0 ? userPii : null;
    }

    public UserPii updateUserPii(UserPii userPii) {
        // TODO: validate userPii
        int updateFlag = userPiiMapper.updateUserPii(userPii);
        return updateFlag > 0 ? userPii : null;
    }

    public void deleteUserPiiById(UUID id) {
        int deleteFlag = userPiiMapper.deleteUserPiiById(id);
        if ( deleteFlag < 1 ) {
            throw new RuntimeException("Failed to delete UserPii with id: " + id);
        }
    }

}