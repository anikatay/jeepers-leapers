package com.neueda.leap.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import com.neueda.leap.util.JwtUtil;

import com.neueda.leap.model.User;
import com.neueda.leap.model.UserPii;
import com.neueda.leap.service.AuthService;
import com.neueda.leap.dto.response.AuthResponse;
import com.neueda.leap.dto.request.LoginRequest;
import com.neueda.leap.dto.request.RegisterRequest;


@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    public AuthController(AuthService authService, JwtUtil jwtUtil) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Login endpoint - validates credentials and returns JWT
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        User user = authService.login(request.getEmail(), request.getPassword());

        String token = jwtUtil.generateToken(request.getEmail(), user.getRole(), user.getUserId());

        return ResponseEntity.ok(new AuthResponse(token, user.getUserId(), user.getRole()));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserPii userPii = new UserPii(null, request.firstName(), request.lastName(), request.dateOfBirth(),
                request.ssn(), request.address(), request.phoneNumber(), request.email());
        User user = authService.register(new User(null, "ROLE_CUSTOMER", "ACTIVE"), userPii, request.password());

        String token = jwtUtil.generateToken(request.email(), user.getRole(), user.getUserId());

        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(token, user.getUserId(), user.getRole()));

    }

}