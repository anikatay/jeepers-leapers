package com.neueda.leap.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import com.neueda.leap.util.JwtSecret;
import java.util.Base64;

@Configuration
public class SecurityConfig {

    @Value("${jwt.shared-secret}")
    private String sharedSecret;

    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(
            Base64.getDecoder().decode(sharedSecret), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - no auth required
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/register").permitAll()
                        .requestMatchers("/public/**").permitAll()
                        
                        // Admin endpoints - ADMIN role only
                        .requestMatchers("/admin/**", "/api/admin/**").hasAuthority("ROLE_ADMIN")
                        
                        // Analyst endpoints - ANALYST or ADMIN
                        .requestMatchers("/analyst/**").hasAnyRole("ANALYST", "ADMIN")
                        
                        // Live market quotes over websockets -public, same as /api/market
                        .requestMatchers("/ws/**").permitAll()

                        .requestMatchers("/internal/**").permitAll()

                        // Administrative list and mutation operations.
                        .requestMatchers(HttpMethod.GET, "/api/users", "/api/accounts/active").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/users", "/api/instruments", "/api/exchanges").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/instruments/*/price", "/api/exchanges/*").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/exchanges/*").hasAuthority("ROLE_ADMIN")

                        // Portfolio endpoints - authenticated users (CLIENT, ANALYST, ADMIN)
                        .requestMatchers("/api/holdings/**").authenticated()
                        .requestMatchers("/api/trades/**").authenticated()
                        .requestMatchers("/api/**").authenticated()

                
                        
                        // Everything else requires authentication
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        
        return http.build();
    }
}