package com.neueda.leap.service;

import com.neueda.leap.exception.ObjectAlreadyExistsException;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.mapper.UserMapper;
import com.neueda.leap.model.User;
import com.neueda.leap.model.UserPii;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserPiiService userPiiService;

    @InjectMocks
    private UserService userService;

    @Test
    void getUserByIdDelegatesToMapper() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "ROLE_CUSTOMER", "ACTIVE");
        when(userMapper.getUserById(userId)).thenReturn(user);

        assertSame(user, userService.getUserById(userId));
        verify(userMapper).getUserById(userId);
    }

    @Test
    void getUserRoleReturnsRoleForExistingUser() {
        UUID userId = UUID.randomUUID();
        when(userMapper.getUserById(userId))
                .thenReturn(new User(userId, "ROLE_ADMIN", "ACTIVE"));

        assertEquals("ROLE_ADMIN", userService.getUserRole(userId));
    }

    @Test
    void getUserRoleThrowsWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(userMapper.getUserById(userId)).thenReturn(null);

        assertThrows(ObjectNotFoundException.class, () -> userService.getUserRole(userId));
    }

    @Test
    void getAllUsersDelegatesToMapper() {
        List<User> users = List.of(new User(UUID.randomUUID(), "ROLE_CUSTOMER", "ACTIVE"));
        when(userMapper.getAllUsers()).thenReturn(users);

        assertSame(users, userService.getAllUsers());
    }

    @Test
    void createUserInsertsAdultUserAndPii() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "ROLE_CUSTOMER", "ACTIVE");
        UserPii userPii = adultUserPii(userId);
        when(userMapper.getUserById(userId)).thenReturn(null);
        when(userMapper.insertUser(user)).thenReturn(1);
        when(userPiiService.insertUserPii(userPii)).thenReturn(userPii);

        assertSame(user, userService.createUser(user, userPii));
        verify(userMapper).insertUser(user);
        verify(userPiiService).insertUserPii(userPii);
    }

    @Test
    void createUserRejectsMinor() {
        UUID userId = UUID.randomUUID();
        UserPii userPii = adultUserPii(userId);
        userPii.setDateOfBirth(OffsetDateTime.now().minusYears(17));

        assertThrows(ObjectInvalidException.class,
                () -> userService.createUser(new User(userId, "ROLE_CUSTOMER", "ACTIVE"), userPii));
        verify(userMapper, never()).insertUser(any(User.class));
    }

    @Test
    void createUserRejectsDuplicate() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "ROLE_CUSTOMER", "ACTIVE");
        when(userMapper.getUserById(userId)).thenReturn(user);

        assertThrows(ObjectAlreadyExistsException.class,
                () -> userService.createUser(user, adultUserPii(userId)));
        verify(userMapper, never()).insertUser(any(User.class));
    }

    @Test
    void createUserRejectsFailedUserInsert() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "ROLE_CUSTOMER", "ACTIVE");
        when(userMapper.getUserById(userId)).thenReturn(null);
        when(userMapper.insertUser(user)).thenReturn(0);

        assertThrows(ObjectNotProcessedException.class,
                () -> userService.createUser(user, adultUserPii(userId)));
        verify(userPiiService, never()).insertUserPii(any(UserPii.class));
    }

    @Test
    void updateUserStatusReturnsSameUserWhenStatusAlreadyMatches() {
        User user = new User(UUID.randomUUID(), "ROLE_CUSTOMER", "ACTIVE");

        assertSame(user, userService.updateUserStatus(user, "ACTIVE"));
        verify(userMapper, never()).updateUserStatus(any(), any());
    }

    @Test
    void updateUserStatusReturnsUpdatedUser() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "ROLE_CUSTOMER", "ACTIVE");
        User updated = new User(userId, "ROLE_CUSTOMER", "INACTIVE");
        when(userMapper.updateUserStatus(userId, "INACTIVE")).thenReturn(1);
        when(userMapper.getUserById(userId)).thenReturn(updated);

        assertSame(updated, userService.updateUserStatus(user, "INACTIVE"));
    }

    @Test
    void deleteUserRejectsMissingUser() {
        UUID userId = UUID.randomUUID();
        when(userMapper.getUserById(userId)).thenReturn(null);

        assertThrows(ObjectNotFoundException.class, () -> userService.deleteUserById(userId));
    }

    @Test
    void deleteUserRejectsActiveUser() {
        UUID userId = UUID.randomUUID();
        when(userMapper.getUserById(userId))
                .thenReturn(new User(userId, "ROLE_CUSTOMER", "ACTIVE"));

        assertThrows(ObjectInvalidException.class, () -> userService.deleteUserById(userId));
        verify(userMapper, never()).deleteUserById(userId);
    }

    @Test
    void deleteUserRemovesInactiveUser() {
        UUID userId = UUID.randomUUID();
        when(userMapper.getUserById(userId))
                .thenReturn(new User(userId, "ROLE_CUSTOMER", "INACTIVE"));

        userService.deleteUserById(userId);

        verify(userMapper).deleteUserById(userId);
    }

    private UserPii adultUserPii(UUID userId) {
        return new UserPii(
                userId,
                "Alex",
                "Smith",
                OffsetDateTime.now().minusYears(30),
                "111-22-3333",
                "123 Main St",
                "555-0100",
                "alex@example.com");
    }
}