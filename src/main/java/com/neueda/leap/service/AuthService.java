package com.neueda.leap.service;

import org.springframework.stereotype.Service;
import com.neueda.leap.model.User;
import java.util.UUID;
import com.neueda.leap.service.UserService;
import com.neueda.leap.service.UserPiiService;

@Service
public class AuthService {

    private final UserService userService;
    private final UserPiiService userPiiService;

    public AuthService(UserService userService, UserPiiService userPiiService) {
        this.userService = userService;
        this.userPiiService = userPiiService;
    }

    public String login(String email, String inputPassword) {
        UUID userId = userPiiService.getUserIdByEmail(email);
        if (userId == null) {
            throw new IllegalArgumentException("User not found");
        }
        User user = userService.getUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        
        // TODO: check password

        return user.getRole();
    }

    public UUID getUserIdByEmail(String email) {
        return userPiiService.getUserIdByEmail(email);
    }

}