package com.neueda.leap.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.neueda.leap.util.JwtUtil;
import com.neueda.leap.service.UserService;
import com.neueda.leap.model.User;
import com.neueda.leap.service.AuthService;
import java.util.UUID;

import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * Login endpoint - validates credentials and returns JWT
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        String role = authService.login(request.getEmail(), request.getPassword());
        if (role == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UUID userId = authService.getUserIdByEmail(request.getEmail());
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        UUID accountId = authService.getAccountIdByUserId(userId);

        // Generate JWT token with user info, role, and userId
        String token = jwtUtil.generateToken(request.getEmail(), role, userId);

        return ResponseEntity.ok(new LoginResponse(token, userId, accountId, role));
    }

    // DTOs for request/response
    public static class LoginRequest {
        private String email;
        private String password;

        public LoginRequest() {}

        public LoginRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class LoginResponse {
        private String token;
        private UUID userId;
        private String role;
        private UUID accountId;

        public LoginResponse(String token, UUID userId, UUID accountId, String role) {
            this.token = token;
            this.userId = userId;
            this.role = role;
            this.accountId = accountId;
        }

        public String getToken() { return token; }
        public UUID getUserId() { return userId; }
        public String getRole() { return role; }
        public UUID getAccountId() { return accountId; }
    }
}