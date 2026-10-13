package com.neueda.leap.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.neueda.leap.exception.ObjectAlreadyExistsException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.mapper.UserMapper;
import com.neueda.leap.service.UserPiiService;
import com.neueda.leap.model.UserPii;

import com.neueda.leap.model.User;
import java.util.UUID;
import java.util.List;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final UserPiiService userPiiService;

    public UserService(UserMapper userMapper, UserPiiService userPiiService) {
        this.userMapper = userMapper;
        this.userPiiService = userPiiService;
    }

    public User getUserById(UUID userId) {
        return userMapper.getUserById(userId);
    }

    public String getPasswordHash(UUID userId) {
        return userMapper.getPasswordHashByUserId(userId);
    }

    public String getUserRole(UUID userId) {
        User user = userMapper.getUserById(userId);
        if (user == null) {
            throw new ObjectNotFoundException("User not found");
        }
        return user.getRole();
    }

    public List<User> getAllUsers() {
        return userMapper.getAllUsers();
    }

    @Transactional
    public User createUser(String role) {
        // TODO: Validate simple stuff with validators

        // Check if user is an adult
        if (userPii.getAge() < 18) {
            throw new ObjectInvalidException("User must be an adult");
        }

        

        UUID newUserId = UUID.randomUUID();

        int insertUserFlag = userMapper.insertUser(user);
        if (insertUserFlag == 0) {
            throw new ObjectNotProcessedException("Failed to insert user");
        }

        int insertPiiFlag = userPiiService.insertUserPii(userPii) != null ? 1 : 0;
        if (insertPiiFlag == 0) {
            throw new ObjectNotProcessedException("Failed to insert user PII");
        }
        return user;
    }

    @Transactional
    public User updateUserStatus(User user, String newStatus) {
        // check if status needs to be updated
        if (user.getStatus().equals(newStatus)) {
            return user;
        }

        int rowsUpdated = userMapper.updateUserStatus(user.getUserId(), newStatus);
        if (rowsUpdated == 0) {
            throw new ObjectNotProcessedException("Failed to update user status");
        }
        return userMapper.getUserById(user.getUserId());
    }

    @Transactional
    public void deleteUserById(UUID userId) {
        // user must be inactive before deletion

        User user = userMapper.getUserById(userId);
        if (user == null) {
            throw new ObjectNotFoundException("User not found");
        }
        if (!"INACTIVE".equals(user.getStatus())) {
            throw new ObjectInvalidException("User must be inactive before deletion");
        }
        userMapper.deleteUserById(userId);
    }

}