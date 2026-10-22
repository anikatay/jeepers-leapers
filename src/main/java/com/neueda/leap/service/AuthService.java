package com.neueda.leap.service;

import org.springframework.stereotype.Service;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import com.neueda.leap.exception.ObjectAlreadyExistsException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.UserPii;
import com.neueda.leap.model.User;
import java.util.UUID;

@Service
public class AuthService {

    private final UserService userService;
    private final UserPiiService userPiiService;
    private final UserCredentialService userCredentialService;
    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;


    public AuthService(UserService userService, UserPiiService userPiiService,
                       UserCredentialService userCredentialService, AccountService accountService,
                       PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.userPiiService = userPiiService;
        this.userCredentialService = userCredentialService;
        this.accountService = accountService;
        this.passwordEncoder = passwordEncoder;
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

        if (!passwordEncoder.matches(inputPassword, passwordHash)) {
            throw new BadCredentialsException("Invalid password");
        }

        User user = userService.getUserById(userId);
        if (user == null) {
            throw new ObjectNotFoundException("User not found for ID: " + userId);
        }
        return user;

    }

    @Transactional
    public User register(User user, UserPii userPii, String inputPassword) {
        UUID existingUserId = userPiiService.findUserIdByEmail(userPii.getEmail());
        if (existingUserId != null) {
            throw new ObjectAlreadyExistsException("User already exists");
        }

        User newUser = userService.createUser(user);

        userPii.setUserId(newUser.getUserId());
        userPiiService.insertUserPii(userPii);

        Account account = new Account();
        account.setUserId(newUser.getUserId());
        accountService.createAccount(account);

        String passwordHash = passwordEncoder.encode(inputPassword);

        userCredentialService.insertUserCredential(newUser.getUserId(), passwordHash);

        return newUser;
    }

    public UUID getAccountIdByUserId(UUID userId){
        return accountService.getAccountIdByUserId(userId);
    }
}