package com.neueda.leap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.dto.request.CreateUserRequest;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.model.User;
import com.neueda.leap.model.UserPii;
import com.neueda.leap.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @Test
    void getUserByIdReturnsUser() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.getUserById(userId))
                .thenReturn(new User(userId, "ROLE_CUSTOMER", "ACTIVE"));

        mockMvc.perform(get("/api/users/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.role").value("ROLE_CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getAllUsersReturnsUsers() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.getAllUsers())
                .thenReturn(List.of(new User(userId, "ROLE_ADMIN", "ACTIVE")));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$[0].role").value("ROLE_ADMIN"));
    }

    @Test
    void createUserBuildsModelsFromRequestAndReturnsCreatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        CreateUserRequest request = validRequest(userId);
        User createdUser = new User(userId, "ROLE_CUSTOMER", "ACTIVE");
        when(userService.createUser(any(User.class), any(UserPii.class))).thenReturn(createdUser);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.role").value("ROLE_CUSTOMER"));

        verify(userService).createUser(any(User.class), any(UserPii.class));
    }

    @Test
    void createUserReturnsBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("INVALID_DATA"));
    }

    @Test
    void updateUserStatusReturnsUpdatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        User existing = new User(userId, "ROLE_CUSTOMER", "ACTIVE");
        User updated = new User(userId, "ROLE_CUSTOMER", "INACTIVE");
        when(userService.getUserById(userId)).thenReturn(existing);
        when(userService.updateUserStatus(existing, "INACTIVE")).thenReturn(updated);

        mockMvc.perform(put("/api/users/{userId}/status", userId)
                        .queryParam("newStatus", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        verify(userService).updateUserStatus(eq(existing), eq("INACTIVE"));
    }

    @Test
    void getUserByIdReturnsNotFoundErrorWhenServiceCannotFindUser() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.getUserById(userId))
                .thenThrow(new ObjectNotFoundException("User not found"));

        mockMvc.perform(get("/api/users/{userId}", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void deleteUserReturnsNoContentAndDelegatesToService() throws Exception {
        UUID userId = UUID.randomUUID();
        doNothing().when(userService).deleteUserById(userId);

        mockMvc.perform(delete("/api/users/{userId}", userId))
                .andExpect(status().isOk());

        verify(userService).deleteUserById(userId);
    }

    @Test
    void deleteUserReturnsNotFoundErrorWhenServiceCannotFindUser() throws Exception {
        UUID userId = UUID.randomUUID();
        doThrow(new ObjectNotFoundException("User not found"))
                .when(userService).deleteUserById(userId);

        mockMvc.perform(delete("/api/users/{userId}", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private CreateUserRequest validRequest(UUID userId) {
        return new CreateUserRequest(
                userId,
                "ROLE_CUSTOMER",
                "ACTIVE",
                "Alex",
                "Smith",
                OffsetDateTime.now().minusYears(30),
                "111-22-3333",
                "123 Main St",
                "5550100",
                "alex@example.com");
    }
}