package com.neueda.leap.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class JwtUtil {

    @Value("${jwt.shared-secret}")
    private String secret;

    private static final long EXPIRATION_TIME = 3600000; // 1 hour in milliseconds

    /**
     * Generate a JWT token for a user with roles
     */
    public String generateToken(String username, List<String> roles) {
        return Jwts.builder()
                .subject(username)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }

    /**
     * Extract username from token
     */
    public String extractUsername(String token) {
        return Jwts.parser()
                .setSigningKey(secret)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    /**
     * Validate token signature and expiry
     */
    public boolean isTokenValid(String token) {
        try {
            Jwts.parser()
                    .setSigningKey(secret)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}