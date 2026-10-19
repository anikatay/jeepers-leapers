package com.neueda.leap.service;

import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.mapper.UserPiiMapper;
import com.neueda.leap.model.UserPii;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPiiServiceTest {

    @Mock
    private UserPiiMapper userPiiMapper;

    @InjectMocks
    private UserPiiService userPiiService;

    // Verifies retrieving a complete PII record delegates to the mapper.
    @Test
    void getUserPiiByIdReturnsMapperResult() {
        UUID userId = UUID.randomUUID();
        UserPii userPii = userPii(userId);
        when(userPiiMapper.getUserPiiById(userId)).thenReturn(userPii);

        assertSame(userPii, userPiiService.getUserPiiById(userId));
        verify(userPiiMapper).getUserPiiById(userId);
    }

    // Verifies each required text field is returned when present.
    @Test
    void gettersReturnFirstNameLastNameEmailAndPhoneNumber() {
        UUID userId = UUID.randomUUID();
        when(userPiiMapper.getUserFirstNameById(userId)).thenReturn("Alex");
        when(userPiiMapper.getUserLastNameById(userId)).thenReturn("Smith");
        when(userPiiMapper.getUserEmailById(userId)).thenReturn("alex@example.com");
        when(userPiiMapper.getUserPhoneNumberById(userId)).thenReturn("5550100");

        assertEquals("Alex", userPiiService.getUserFirstNameById(userId));
        assertEquals("Smith", userPiiService.getUserLastNameById(userId));
        assertEquals("alex@example.com", userPiiService.getUserEmailById(userId));
        assertEquals("5550100", userPiiService.getUserPhoneNumberById(userId));
    }

    // Verifies missing or empty text fields raise the appropriate not-found error.
    @Test
    void gettersRejectNullAndEmptyValues() {
        UUID userId = UUID.randomUUID();
        when(userPiiMapper.getUserFirstNameById(userId)).thenReturn(null);
        when(userPiiMapper.getUserLastNameById(userId)).thenReturn("");
        when(userPiiMapper.getUserEmailById(userId)).thenReturn(null);
        when(userPiiMapper.getUserPhoneNumberById(userId)).thenReturn("");

        assertThrows(ObjectNotFoundException.class, () -> userPiiService.getUserFirstNameById(userId));
        assertThrows(ObjectNotFoundException.class, () -> userPiiService.getUserLastNameById(userId));
        assertThrows(ObjectNotFoundException.class, () -> userPiiService.getUserEmailById(userId));
        assertThrows(ObjectNotFoundException.class, () -> userPiiService.getUserPhoneNumberById(userId));
    }

    // Verifies email lookup rejects null and empty input before mapper access.
    @Test
    void getUserIdByEmailRejectsNullAndEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> userPiiService.getUserIdByEmail(null));
        assertThrows(IllegalArgumentException.class, () -> userPiiService.getUserIdByEmail(""));
        verify(userPiiMapper, never()).getUserIdByEmail(any());
    }

    // Verifies email lookup returns an id and reports an unknown email.
    @Test
    void getUserIdByEmailReturnsIdOrThrowsWhenMissing() {
        UUID userId = UUID.randomUUID();
        when(userPiiMapper.getUserIdByEmail("alex@example.com")).thenReturn(userId);
        when(userPiiMapper.getUserIdByEmail("missing@example.com")).thenReturn(null);

        assertEquals(userId, userPiiService.getUserIdByEmail("alex@example.com"));
        assertThrows(ObjectNotFoundException.class,
                () -> userPiiService.getUserIdByEmail("missing@example.com"));
    }

    // Verifies successful PII insertion returns the original object.
    @Test
    void insertUserPiiReturnsInputWhenMapperSucceeds() {
        UserPii userPii = userPii(UUID.randomUUID());
        when(userPiiMapper.insertUserPii(userPii)).thenReturn(1);

        assertSame(userPii, userPiiService.insertUserPii(userPii));
        verify(userPiiMapper).insertUserPii(userPii);
    }

    // Verifies failed PII insertion is converted to a processing exception.
    @Test
    void insertUserPiiThrowsWhenMapperDoesNotInsert() {
        UserPii userPii = userPii(UUID.randomUUID());
        when(userPiiMapper.insertUserPii(userPii)).thenReturn(0);

        assertThrows(ObjectNotProcessedException.class, () -> userPiiService.insertUserPii(userPii));
    }

    // Verifies successful PII updates return the original object.
    @Test
    void updateUserPiiReturnsInputWhenMapperSucceeds() {
        UserPii userPii = userPii(UUID.randomUUID());
        when(userPiiMapper.updateUserPii(userPii)).thenReturn(1);

        assertSame(userPii, userPiiService.updateUserPii(userPii));
        verify(userPiiMapper).updateUserPii(userPii);
    }

    // Verifies failed PII updates are converted to a processing exception.
    @Test
    void updateUserPiiThrowsWhenMapperDoesNotUpdate() {
        UserPii userPii = userPii(UUID.randomUUID());
        when(userPiiMapper.updateUserPii(userPii)).thenReturn(0);

        assertThrows(ObjectNotProcessedException.class, () -> userPiiService.updateUserPii(userPii));
    }

    // Verifies successful deletion delegates to the mapper.
    @Test
    void deleteUserPiiByIdSucceedsWhenMapperDeletesRow() {
        UUID userId = UUID.randomUUID();
        when(userPiiMapper.deleteUserPiiById(userId)).thenReturn(1);

        userPiiService.deleteUserPiiById(userId);

        verify(userPiiMapper).deleteUserPiiById(userId);
    }

    // Verifies failed deletion is converted to a processing exception.
    @Test
    void deleteUserPiiByIdThrowsWhenMapperDoesNotDelete() {
        UUID userId = UUID.randomUUID();
        when(userPiiMapper.deleteUserPiiById(userId)).thenReturn(0);

        assertThrows(ObjectNotProcessedException.class, () -> userPiiService.deleteUserPiiById(userId));
    }

    private UserPii userPii(UUID userId) {
        return new UserPii(
                userId,
                "Alex",
                "Smith",
                OffsetDateTime.parse("1990-05-20T00:00:00Z"),
                "111-22-3333",
                "123 Main St",
                "5550100",
                "alex@example.com");
    }
}