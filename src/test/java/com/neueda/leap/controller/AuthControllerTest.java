package com.neueda.leap.controller;

import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.model.User;
import com.neueda.leap.service.AccountService;
import com.neueda.leap.service.AuthService;
import com.neueda.leap.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private AccountService accountService;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void loginReturnsTokenAndUserDetailsForValidRequest() throws Exception {
        String email = "alex@example.com";
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(authService.login(email, "password"))
            .thenReturn(new User(userId, "ROLE_CUSTOMER", "ACTIVE"));
        when(accountService.getAccountIdByUserId(userId)).thenReturn(accountId);
        when(jwtUtil.generateToken(email, "ROLE_CUSTOMER", userId, accountId)).thenReturn("jwt-token");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alex@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.role").value("ROLE_CUSTOMER"));

        verify(jwtUtil).generateToken(eq(email), eq("ROLE_CUSTOMER"), eq(userId), eq(accountId));
    }

    @Test
    void loginReturnsUnauthorizedWhenCredentialsAreRejected() throws Exception {
        when(authService.login("alex@example.com", "wrong-password"))
            .thenThrow(new BadCredentialsException("Invalid password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alex@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginReturnsNotFoundWhenUserIdIsMissing() throws Exception {
        String email = "alex@example.com";
        when(authService.login(email, "password"))
            .thenThrow(new ObjectNotFoundException("User ID not found for email: " + email));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alex@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void loginReturnsNotFoundErrorWhenAuthServiceCannotFindUser() throws Exception {
        when(authService.login("missing@example.com", "password"))
                .thenThrow(new ObjectNotFoundException("User not found"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void loginReturnsBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_DATA"));
    }
}