package com.neueda.leap.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.neueda.leap.exception.ObjectAlreadyExistsException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.mapper.UserMapper;

import com.neueda.leap.model.User;
import java.util.UUID;
import java.util.List;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
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
    public User createUser(User user) {
        if (user == null) {
            throw new ObjectInvalidException("User is required");
        }
        if (user.getUserId() == null) {
            user.setUserId(UUID.randomUUID());
        }
        if (user.getRole() == null || user.getRole().isBlank()) {
            user.setRole("ROLE_CUSTOMER");
        }
        if (user.getStatus() == null || user.getStatus().isBlank()) {
            user.setStatus("ACTIVE");
        }
        if (userMapper.insertUser(user) == 0) {
            throw new ObjectNotProcessedException("Failed to insert user");
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