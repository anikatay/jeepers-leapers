package com.neueda.leap.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import com.neueda.leap.util.JwtUtil;

import com.neueda.leap.model.User;
import com.neueda.leap.model.UserPii;
import com.neueda.leap.service.AccountService;
import com.neueda.leap.service.AuthService;
import com.neueda.leap.dto.response.AuthResponse;
import com.neueda.leap.dto.request.LoginRequest;
import com.neueda.leap.dto.request.RegisterRequest;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final AccountService accountService;

    public AuthController(AuthService authService, JwtUtil jwtUtil, AccountService accountService) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
        this.accountService = accountService;
    }

    /**
     * Login endpoint - validates credentials and returns JWT
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        User user = authService.login(request.email(), request.password());
        UUID accountId = accountService.getAccountIdByUserId(user.getUserId());

        String token = jwtUtil.generateToken(request.email(), user.getRole(), user.getUserId(), accountId);

        return ResponseEntity.ok(new AuthResponse(token, user.getUserId(), accountId, user.getRole()));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserPii userPii = new UserPii(null, request.firstName(), request.lastName(), request.dateOfBirth(),
                request.ssn(), request.address(), request.phoneNumber(), request.email());
        User user = authService.register(new User(null, "ROLE_CUSTOMER", "ACTIVE"), userPii, request.password());
        UUID accountId = accountService.getAccountIdByUserId(user.getUserId());

        String token = jwtUtil.generateToken(request.email(), user.getRole(), user.getUserId(), accountId);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new AuthResponse(token, user.getUserId(), accountId, user.getRole()));

    }

}