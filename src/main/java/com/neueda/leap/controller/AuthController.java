package com.neueda.leap.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.neueda.leap.util.JwtUtil;

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

    /**
     * Login endpoint - validates credentials and returns JWT
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        User user = authService.login(request.getEmail(), request.getPassword());

        String token = jwtUtil.generateToken(request.getEmail(), user.getRole(), user.getId());

        return ResponseEntity.ok(new AuthResponse(token, user.getId(), user.getRole()));
    }

    @Transactional
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {

    }

}