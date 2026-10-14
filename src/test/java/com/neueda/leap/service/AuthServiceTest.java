package com.neueda.leap.service;

import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private UserPiiService userPiiService;

    @InjectMocks
    private AuthService authService;

    @Test
    void loginReturnsUserRoleForKnownEmail() {
        String email = "alex@example.com";
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "ROLE_CUSTOMER", "ACTIVE");

        when(userPiiService.getUserIdByEmail(email)).thenReturn(userId);
        when(userService.getUserById(userId)).thenReturn(user);

        assertEquals("ROLE_CUSTOMER", authService.login(email, "password"));
        verify(userPiiService).getUserIdByEmail(email);
        verify(userService).getUserById(userId);
    }

    @Test
    void loginThrowsWhenEmailHasNoUserId() {
        String email = "missing@example.com";
        when(userPiiService.getUserIdByEmail(email)).thenReturn(null);

        ObjectNotFoundException exception = assertThrows(
                ObjectNotFoundException.class,
                () -> authService.login(email, "password"));

        assertEquals("User ID not found for email: " + email, exception.getMessage());
    }

    @Test
    void loginThrowsWhenUserRecordIsMissing() {
        String email = "alex@example.com";
        UUID userId = UUID.randomUUID();
        when(userPiiService.getUserIdByEmail(email)).thenReturn(userId);
        when(userService.getUserById(userId)).thenReturn(null);

        ObjectNotFoundException exception = assertThrows(
                ObjectNotFoundException.class,
                () -> authService.login(email, "wrong-password"));

        assertEquals("User not found for ID: " + userId, exception.getMessage());
    }

    @Test
    void getUserIdByEmailDelegatesToPiiService() {
        String email = "alex@example.com";
        UUID userId = UUID.randomUUID();
        when(userPiiService.getUserIdByEmail(email)).thenReturn(userId);

        assertEquals(userId, authService.getUserIdByEmail(email));
        verify(userPiiService).getUserIdByEmail(email);
    }
}