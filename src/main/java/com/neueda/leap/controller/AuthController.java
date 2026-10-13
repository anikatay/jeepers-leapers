package com.neueda.leap.controller;
/*
CHECK FILES
AuthController
UserMapper
AuthService
UserService
*/
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.authentication.BadCredentialsException;

import com.neueda.leap.util.JwtUtil;

import com.neueda.leap.service.UserCredentialService;
import com.neueda.leap.service.UserService;
import com.neueda.leap.model.User;
import com.neueda.leap.service.AuthService;
import com.neueda.leap.dto.response.AuthResponse;
import com.neueda.leap.dto.request.LoginRequest;
import com.neueda.leap.dto.request.RegisterRequest;

import java.util.UUID;
import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserCredentialService userCredentialService;

    /**
     * Login endpoint - validates credentials and returns JWT
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        String role = authService.login(request.getEmail(), request.getPassword());
        if (role == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UUID userId = authService.getUserIdByEmail(request.getEmail());
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        UUID accountId = authService.getAccountIdByUserId(userId);

        if (request.getPassword() == null) {
            throw new BadCredentialsException("Password cannot be null");
        }

        String passwordHash = userCredentialService.getPasswordHashByUserId(userId);

        if (bcrypt.compare(request.getPassword(), passwordHash)) {
            // Generate JWT token with user info, role, and userId
            String token = jwtUtil.generateToken(request.getEmail(), role, userId);

            return ResponseEntity.ok(new AuthResponse(token, userId, role));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
    }

}