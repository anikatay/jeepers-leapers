package com.neueda.leap.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.neueda.leap.util.JwtUtil;

import com.neueda.leap.service.UserService;
import com.neueda.leap.model.User;
import com.neueda.leap.service.AuthService;
import com.neueda.leap.dto.response.AuthResponse;
import com.neueda.leap.dto.request.LoginRequest;

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
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        String role = authService.login(request.getEmail(), request.getPassword());
        if (role == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UUID userId = authService.getUserIdByEmail(request.getEmail());
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Generate JWT token with user info, role, and userId
        String token = jwtUtil.generateToken(request.getEmail(), role, userId);

        return ResponseEntity.ok(new AuthResponse(token, userId, role));
    }

}