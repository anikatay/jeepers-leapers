package com.neueda.leap.service;

import org.springframework.stereotype.Service;
import org.springframework.security.authentication.BadCredentialsException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.model.User;
import java.util.UUID;
import com.neueda.leap.service.UserService;
import com.neueda.leap.service.UserPiiService;
import com.neueda.leap.service.UserCredentialService;

@Service
public class AuthService {

    private final UserService userService;
    private final UserPiiService userPiiService;
    private final UserCredentialService userCredentialService;


    public AuthService(UserService userService, UserPiiService userPiiService, UserCredentialService userCredentialService) {
        this.userService = userService;
        this.userPiiService = userPiiService;
        this.userCredentialService = userCredentialService;
    }

    public User login(String email, String inputPassword) {
        UUID userId = userPiiService.getUserIdByEmail(email);
        if (userId == null) {
            throw new ObjectNotFoundException("User ID not found for email: " + email);
        }
        
        if (inputPassword == null) {
            throw new BadCredentialsException("Password cannot be null");
        }

        String passwordHash = userCredentialService.getPasswordHashByUserId(userId);
        if (passwordHash == null) {
            throw new ObjectNotFoundException("Password hash not found for user ID: " + userId);
        }

        if (bcrypt.compare(inputPassword, passwordHash)) {
            User user = userService.getUserById(userId);
            if (user == null) {
                throw new ObjectNotFoundException("User not found for ID: " + userId);
            }
            return user;
        } else {
            throw new BadCredentialsException("Invalid password");
        }

    }

    public User registerNewUser(User user, UserPii userPii) {

        // Check if user already exists
        UUID existingUserId = userPiiService.getUserIdByEmail(userPii.getEmail());
        if (existingUserId != null) {
            throw new ObjectAlreadyExistsException("User already exists");
        }
        
        User newUser = userService.createUser(user.getRole());

        UUID userId = userPiiService.createUserPii(userPii);
        user.setId(userId);
        userService.createUser(user);
        userPiiService.createUserPii(userPii);
    }

}