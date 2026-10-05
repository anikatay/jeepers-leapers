package com.neueda.leap.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    public List<User> getAllUsers() {
        return userMapper.getAllUsers();
    }

    @Transactional
    public User createUser(User user, UserPii userPii) {
        // TODO: Validate simple stuff with validators

        // Check if user is an adult
        if (userPii.getAge() < 18) {
            // TODO: make new exception?
            throw new IllegalArgumentException("User must be an adult");
        }

        // Check if user already exists
        User existingUser = userMapper.getUserById(user.getUserId());
        if (existingUser != null) {
            throw new IllegalArgumentException("User already exists");
        }

        int rowsInserted = userMapper.insertUser(user);
        if (rowsInserted == 0) {
            throw new IllegalStateException("Failed to insert user");
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
            throw new IllegalStateException("Failed to update user status");
        }
        return userMapper.getUserById(user.getUserId());
    }

    @Transactional
    public void deleteUserById(UUID userId) {
        // user must be inactive before deletion

        User user = userMapper.getUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        if (!"INACTIVE".equals(user.getStatus())) {
            throw new IllegalStateException("User must be inactive before deletion");
        }
        userMapper.deleteUserById(userId);
    }

}