package com.neueda.leap.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(
            "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
    }

    // Verifies generated tokens contain the expected subject and claims.
    @Test
    void generateTokenCreatesReadableToken() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        String token = jwtUtil.generateToken("alex@example.com", "ROLE_CUSTOMER", userId, accountId);

        assertEquals("alex@example.com", jwtUtil.extractUsername(token));
        assertTrue(jwtUtil.isTokenValid(token));
    }

    // Verifies a token signed with another key is rejected.
    @Test
    void isTokenValidReturnsFalseForInvalidSignature() {
        UUID userId = UUID.randomUUID();
        String otherSecret = Base64.getEncoder().encodeToString(
                "abcdefghijklmnopqrstuvwxyz123456".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("alex@example.com")
                .claim("role", "ROLE_CUSTOMER")
                .claim("userId", userId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(otherSecret)))
                .compact();

        assertFalse(jwtUtil.isTokenValid(token));
    }

    // Verifies expired tokens fail validation.
    @Test
    void isTokenValidReturnsFalseForExpiredToken() {
        String token = Jwts.builder()
                .subject("alex@example.com")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
                .compact();

        assertFalse(jwtUtil.isTokenValid(token));
    }

    // Verifies malformed token input fails validation without escaping the method.
    @Test
    void isTokenValidReturnsFalseForMalformedToken() {
        assertFalse(jwtUtil.isTokenValid("not-a-jwt"));
    }
}