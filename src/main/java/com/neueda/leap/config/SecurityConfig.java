package com.neueda.leap.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

    @Value("${jwt.shared-secret}")
    private String sharedSecret;

    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(
                sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - no auth required
                        .requestMatchers("/auth/login").permitAll()
                        .requestMatchers("/public/**").permitAll()
                        
                        // Admin endpoints - ADMIN role only
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        
                        // Analyst endpoints - ANALYST or ADMIN
                        .requestMatchers("/analyst/**").hasAnyRole("ANALYST", "ADMIN")
                        
                        
                        // Portfolio endpoints - authenticated users (CLIENT, ANALYST, ADMIN)
                        .requestMatchers("/portfolio/**").authenticated()
                        .requestMatchers("/trades/**").authenticated()
                        
                        // Everything else requires authentication
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        
        return http.build();
    }
}