package com.neueda.leap.service;

import org.springframework.stereotype.Service;
import com.neueda.leap.mapper.UserCredentialMapper;
import org.springframework.beans.factory.annotation.Autowired;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import java.util.UUID;

@Service
public class UserCredentialService {

    private final UserCredentialMapper userCredentialMapper;

    @Autowired
    public UserCredentialService(UserCredentialMapper userCredentialMapper) {
        this.userCredentialMapper = userCredentialMapper;
    }

    public String getPasswordHashByUserId(UUID userId) {
        String hash = userCredentialMapper.getPasswordHashByUserId(userId);
        if (hash == null) {
            throw new ObjectNotFoundException("User credentials not found");
        }
        return hash;
    }

    public void insertUserCredential(UUID userId, String passwordHash) {
        int insertFlag = userCredentialMapper.insertUserCredential(userId, passwordHash);
        if (insertFlag < 1) {
            throw new ObjectNotProcessedException("Failed to add user credentials for userId: " + userId);
        }
    }

    public void updateUserCredential(UUID userId, String passwordHash) {
        int updateFlag = userCredentialMapper.updateUserCredential(userId, passwordHash);
        if (updateFlag < 1) {
            throw new ObjectNotProcessedException("Failed to update user credentials for userId: " + userId);
        }
    }

    public void deleteUserCredential(UUID userId) {
        int deleteFlag = userCredentialMapper.deleteUserCredential(userId);
        if (deleteFlag < 1) {
            throw new ObjectNotProcessedException("Failed to delete user credentials for userId: " + userId);
        }
    }

}