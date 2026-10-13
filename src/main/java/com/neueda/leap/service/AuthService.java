package com.neueda.leap.service;

import org.springframework.stereotype.Service;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.model.User;
import java.util.UUID;
import com.neueda.leap.service.UserService;
import com.neueda.leap.service.UserPiiService;

@Service
public class AuthService {

    private final UserService userService;
    private final UserPiiService userPiiService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserService userService, UserPiiService userPiiService,
            PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.userPiiService = userPiiService;
        this.passwordEncoder = passwordEncoder;
    }

    public String login(String email, String inputPassword) {
        UUID userId = userPiiService.getUserIdByEmail(email);
        if (userId == null) {
            throw new ObjectNotFoundException("User ID not found for email: " + email);
        }
        User user = userService.getUserById(userId);
        if (user == null) {
            throw new ObjectNotFoundException("User not found for ID: " + userId);
        }
        
        String passwordHash = userService.getPasswordHash(userId);
        if (inputPassword == null || passwordHash == null
                || !passwordEncoder.matches(inputPassword, passwordHash)) {
            throw new BadCredentialsException("Invalid email or password");
        }

        return user.getRole();
    }

    public UUID getUserIdByEmail(String email) {
        return userPiiService.getUserIdByEmail(email);
    }

}