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
@RequestMapping("/auth")
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
        try {
            String role = authService.login(request.getEmail(), request.getPassword());
            UUID userId = authService.getUserIdByEmail(request.getEmail());

            // Generate JWT token with user info and role
            String token = jwtUtil.generateToken(request.getEmail(), role);

            return ResponseEntity.ok(new LoginResponse(token, userId, role));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // DTOs for request/response
    public static class LoginRequest {
        private String email;
        private String password;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class LoginResponse {
        private String token;
        private UUID userId;
        private String role;

        public LoginResponse(String token, UUID userId, String role) {
            this.token = token;
            this.userId = userId;
            this.role = role;
        }

        public String getToken() { return token; }
        public UUID getUserId() { return userId; }
        public String getRole() { return role; }
    }
}