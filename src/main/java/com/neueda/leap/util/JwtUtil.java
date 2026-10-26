package com.neueda.leap.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    @Value("${jwt.shared-secret}")
    private String secret;

    private static final long EXPIRATION_TIME = 3600000; // 1 hour in milliseconds

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(JwtSecret.keyBytes(secret));
    }

    /**
     * Generate a JWT token for a user with a role, userId, and accountId
     */
    public String generateToken(String username, String role, UUID userId, UUID accountId) {
        var builder = Jwts.builder()
            .subject(userId.toString())
            .claim("role", role)
            .claim("email", username)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME));

        if (accountId != null) {
            builder.claim("accountId", accountId.toString());
        }

        return builder
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Extract username from token
     */
    public String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /**
     * Validate token signature and expiry
     */
    public boolean isTokenValid(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}