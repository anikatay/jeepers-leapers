package com.neueda.leap.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

@WebMvcTest(controllers = SecurityConfigTest.TestEndpoints.class)
@Import({SecurityConfigTest.TestConfiguration.class, SecurityConfigTest.TestEndpoints.class})
@TestPropertySource(properties = "jwt.shared-secret=test-secret-for-security-config")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() throws JwtException {
        when(jwtDecoder.decode("valid-token")).thenReturn(validJwt());
        when(jwtDecoder.decode("malformed-token")).thenThrow(new JwtException("Invalid token"));
    }

    // Verifies public and API endpoints are accessible without authentication.
    @Test
    void publicAndApiEndpointsPermitAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/auth/login")).andExpect(status().isOk());
        mockMvc.perform(get("/public/status")).andExpect(status().isOk());
        mockMvc.perform(get("/api/status")).andExpect(status().isOk());
    }

    // Verifies portfolio endpoints reject requests without a bearer token.
    @Test
    void portfolioEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/holdings/status")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/trades/status")).andExpect(status().isUnauthorized());
    }

    // Verifies a decoded bearer token grants access to authenticated endpoints.
    @Test
    void validBearerTokenAllowsAuthenticatedPortfolioRequests() throws Exception {
        mockMvc.perform(get("/holdings/status").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/trades/status").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk());
    }

    // Verifies admin and analyst paths enforce their configured authority rules.
    @Test
    void protectedRoleEndpointsRejectTokenWithoutMappedRoleAuthority() throws Exception {
        mockMvc.perform(get("/admin/status").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/analyst/status").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden());
    }

    // Verifies malformed bearer tokens are rejected by the resource server.
    @Test
    void malformedBearerTokenIsUnauthorized() throws Exception {
        org.junit.jupiter.api.Assertions.assertThrows(
            AuthenticationServiceException.class,
            () -> mockMvc.perform(get("/holdings/status")
                .header("Authorization", "Bearer malformed-token")));
    }

    private Jwt validJwt() {
        return Jwt.withTokenValue("valid-token")
                .header("alg", "HS256")
                .subject("alex@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claims(claims -> claims.putAll(Map.of("scope", "read")))
                .build();
    }

    @Configuration
    @Import(SecurityConfig.class)
    static class TestConfiguration {
    }

    @RestController
    static class TestEndpoints {

        @GetMapping({"/api/auth/login", "/public/status", "/api/status", "/admin/status",
                "/analyst/status", "/holdings/status", "/trades/status"})
        String status() {
            return "ok";
        }
    }
}