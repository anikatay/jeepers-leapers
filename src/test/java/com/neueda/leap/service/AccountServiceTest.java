package com.neueda.leap.service;

import com.neueda.leap.dto.response.AccountResponse;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.mapper.AccountMapper;
import com.neueda.leap.model.Account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountMapper accountMapper;

    @InjectMocks
    private AccountService accountService;

    // Test createAccount method

    // Verifies that createAccount successfully creates a new account and generates accountId and timestamp
    @Test
    void testCreateAccountSuccessfully() {
        Account account = new Account();
        account.setUserId(UUID.randomUUID());
        account.setBalance(BigDecimal.valueOf(100));
        account.setCurrency("USD");

        when(accountMapper.createAccount(any(Account.class))).thenReturn(1);

        Account result = accountService.createAccount(account);

        assertNotNull(result);
        assertNotNull(result.getAccountId());
        assertEquals("USD", result.getCurrency());
        assertEquals(BigDecimal.valueOf(100), result.getBalance());
        assertEquals("ACTIVE", result.getStatus());
        assertNotNull(result.getCreatedAt());
        verify(accountMapper, times(1)).createAccount(any(Account.class));
    }

    // Verifies that createAccount throws ObjectNotFoundException when account is null
    @Test
    void testCreateAccountThrowsExceptionWhenAccountIsNull() {
        assertThrows(ObjectNotFoundException.class, () -> {
            accountService.createAccount(null);
        });

        verify(accountMapper, never()).createAccount(any(Account.class));
    }

    // Verifies that createAccount throws ObjectNotFoundException when userId is null
    @Test
    void testCreateAccountThrowsExceptionWhenUserIdIsNull() {
        Account account = new Account();
        account.setBalance(BigDecimal.valueOf(100));

        assertThrows(ObjectNotFoundException.class, () -> {
            accountService.createAccount(account);
        });

        verify(accountMapper, never()).createAccount(any(Account.class));
    }

    // Verifies that createAccount throws ObjectNotProcessedException when mapper fails
    @Test
    void testCreateAccountThrowsExceptionWhenMapperFails() {
        Account account = new Account();
        account.setUserId(UUID.randomUUID());
        account.setBalance(BigDecimal.valueOf(100));

        when(accountMapper.createAccount(any(Account.class))).thenReturn(0);

        assertThrows(ObjectNotProcessedException.class, () -> {
            accountService.createAccount(account);
        });

        verify(accountMapper, times(1)).createAccount(any(Account.class));
    }

    // Verifies that createAccount sets balance to BigDecimal.ZERO when balance is null
    @Test
    void testCreateAccountSetsDefaultBalanceWhenNull() {
        Account account = new Account();
        account.setUserId(UUID.randomUUID());
        // balance is intentionally null

        when(accountMapper.createAccount(any(Account.class))).thenReturn(1);

        Account result = accountService.createAccount(account);

        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getBalance());
        verify(accountMapper, times(1)).createAccount(any(Account.class));
    }

    // Test getAccount method

    // Verifies that getAccount successfully retrieves an existing account by ID
    @Test
    void testGetAccountSuccessfully() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        account.setAccountId(accountId);
        account.setUserId(UUID.randomUUID());
        account.setBalance(BigDecimal.valueOf(500));
        account.setStatus("ACTIVE");

        when(accountMapper.findByAccountId(accountId)).thenReturn(account);

        Account result = accountService.getAccount(accountId);

        assertNotNull(result);
        assertEquals(accountId, result.getAccountId());
        assertEquals(BigDecimal.valueOf(500), result.getBalance());
        verify(accountMapper, times(1)).findByAccountId(accountId);
    }

    // Verifies that getAccount throws ObjectNotFoundException when accountId is null
    @Test
    void testGetAccountThrowsExceptionWhenAccountIdIsNull() {
        assertThrows(ObjectNotFoundException.class, () -> {
            accountService.getAccount(null);
        });

        verify(accountMapper, never()).findByAccountId(any(UUID.class));
    }

    // Verifies that getAccount throws ObjectNotFoundException when account does not exist
    @Test
    void testGetAccountThrowsExceptionWhenAccountNotFound() {
        UUID accountId = UUID.randomUUID();

        when(accountMapper.findByAccountId(accountId)).thenReturn(null);

        assertThrows(ObjectNotFoundException.class, () -> {
            accountService.getAccount(accountId);
        });

        verify(accountMapper, times(1)).findByAccountId(accountId);
    }

    // Test updateBalance method

    // Verifies that updateBalance successfully updates account balance and last modified timestamp
    @Test
    void testUpdateBalanceSuccessfully() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        account.setAccountId(accountId);
        account.setStatus("ACTIVE");
        account.setBalance(BigDecimal.valueOf(500));

        when(accountMapper.findByAccountId(accountId)).thenReturn(account);
        when(accountMapper.updateBalance(accountId, BigDecimal.valueOf(100))).thenReturn(1);
        when(accountMapper.updateLastModified(eq(accountId), any(OffsetDateTime.class))).thenReturn(1);

        assertDoesNotThrow(() -> {
            accountService.updateBalance(accountId, BigDecimal.valueOf(100));
        });

        verify(accountMapper, times(1)).updateBalance(accountId, BigDecimal.valueOf(100));
        verify(accountMapper, times(1)).updateLastModified(eq(accountId), any(OffsetDateTime.class));
    }

    // Verifies that updateBalance throws ObjectNotFoundException when accountId is null
    @Test
    void testUpdateBalanceThrowsExceptionWhenAccountIdIsNull() {
        assertThrows(ObjectNotFoundException.class, () -> {
            accountService.updateBalance(null, BigDecimal.valueOf(100));
        });

        verify(accountMapper, never()).updateBalance(any(UUID.class), any(BigDecimal.class));
    }

    // Verifies that updateBalance throws ObjectNotFoundException when amount is null
    @Test
    void testUpdateBalanceThrowsExceptionWhenAmountIsNull() {
        UUID accountId = UUID.randomUUID();

        assertThrows(ObjectNotFoundException.class, () -> {
            accountService.updateBalance(accountId, null);
        });

        verify(accountMapper, never()).updateBalance(any(UUID.class), any(BigDecimal.class));
    }

    // Verifies that updateBalance throws ObjectNotFoundException when amount is negative
    @Test
    void testUpdateBalanceThrowsExceptionWhenAmountIsNegative() {
        UUID accountId = UUID.randomUUID();

        assertThrows(ObjectNotFoundException.class, () -> {
            accountService.updateBalance(accountId, BigDecimal.valueOf(-100));
        });

        verify(accountMapper, never()).updateBalance(any(UUID.class), any(BigDecimal.class));
    }

    // Verifies that updateBalance throws ObjectInvalidException when account status is not ACTIVE
    @Test
    void testUpdateBalanceThrowsExceptionWhenAccountNotActive() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        account.setAccountId(accountId);
        account.setStatus("CLOSED");

        when(accountMapper.findByAccountId(accountId)).thenReturn(account);

        assertThrows(ObjectInvalidException.class, () -> {
            accountService.updateBalance(accountId, BigDecimal.valueOf(100));
        });

        verify(accountMapper, never()).updateBalance(any(UUID.class), any(BigDecimal.class));
    }

    // Verifies that updateBalance throws ObjectNotProcessedException when balance update fails
    @Test
    void testUpdateBalanceThrowsExceptionWhenBalanceUpdateFails() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        account.setAccountId(accountId);
        account.setStatus("ACTIVE");

        when(accountMapper.findByAccountId(accountId)).thenReturn(account);
        when(accountMapper.updateBalance(accountId, BigDecimal.valueOf(100))).thenReturn(0);

        assertThrows(ObjectNotProcessedException.class, () -> {
            accountService.updateBalance(accountId, BigDecimal.valueOf(100));
        });

        verify(accountMapper, never()).updateLastModified(any(UUID.class), any(OffsetDateTime.class));
    }

    // Verifies that updateBalance throws ObjectNotProcessedException when last modified update fails
    @Test
    void testUpdateBalanceThrowsExceptionWhenLastModifiedUpdateFails() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        account.setAccountId(accountId);
        account.setStatus("ACTIVE");

        when(accountMapper.findByAccountId(accountId)).thenReturn(account);
        when(accountMapper.updateBalance(accountId, BigDecimal.valueOf(100))).thenReturn(1);
        when(accountMapper.updateLastModified(eq(accountId), any(OffsetDateTime.class))).thenReturn(0);

        assertThrows(ObjectNotProcessedException.class, () -> {
            accountService.updateBalance(accountId, BigDecimal.valueOf(100));
        });

        verify(accountMapper, times(1)).updateBalance(accountId, BigDecimal.valueOf(100));
    }

    // Test closeAccount method

    // Verifies that closeAccount successfully closes an account and updates last modified timestamp
    @Test
    void testCloseAccountSuccessfully() {
        UUID accountId = UUID.randomUUID();

        when(accountMapper.updateStatus(accountId, "CLOSED")).thenReturn(1);
        when(accountMapper.updateLastModified(eq(accountId), any(OffsetDateTime.class))).thenReturn(1);

        assertDoesNotThrow(() -> {
            accountService.closeAccount(accountId);
        });

        verify(accountMapper, times(1)).updateStatus(accountId, "CLOSED");
        verify(accountMapper, times(1)).updateLastModified(eq(accountId), any(OffsetDateTime.class));
    }

    // Verifies that closeAccount throws ObjectNotFoundException when accountId is null
    @Test
    void testCloseAccountThrowsExceptionWhenAccountIdIsNull() {
        assertThrows(ObjectNotFoundException.class, () -> {
            accountService.closeAccount(null);
        });

        verify(accountMapper, never()).updateStatus(any(UUID.class), anyString());
    }

    // Verifies that closeAccount throws ObjectNotProcessedException when status update fails
    @Test
    void testCloseAccountThrowsExceptionWhenStatusUpdateFails() {
        UUID accountId = UUID.randomUUID();

        when(accountMapper.updateStatus(accountId, "CLOSED")).thenReturn(0);

        assertThrows(ObjectNotProcessedException.class, () -> {
            accountService.closeAccount(accountId);
        });

        verify(accountMapper, never()).updateLastModified(any(UUID.class), any(OffsetDateTime.class));
    }

    // Verifies that closeAccount throws ObjectNotProcessedException when last modified update fails
    @Test
    void testCloseAccountThrowsExceptionWhenLastModifiedUpdateFails() {
        UUID accountId = UUID.randomUUID();

        when(accountMapper.updateStatus(accountId, "CLOSED")).thenReturn(1);
        when(accountMapper.updateLastModified(eq(accountId), any(OffsetDateTime.class))).thenReturn(0);

        assertThrows(ObjectNotProcessedException.class, () -> {
            accountService.closeAccount(accountId);
        });

        verify(accountMapper, times(1)).updateStatus(accountId, "CLOSED");
    }

    // Test incrementBalance method

    // Verifies that incrementBalance successfully increments account balance and updates last modified timestamp
    @Test
    void testIncrementBalanceSuccessfully() {
        UUID accountId = UUID.randomUUID();

        when(accountMapper.incrementBalance(accountId, BigDecimal.valueOf(250))).thenReturn(1);
        when(accountMapper.updateLastModified(eq(accountId), any(OffsetDateTime.class))).thenReturn(1);

        assertDoesNotThrow(() -> {
            accountService.incrementBalance(accountId, BigDecimal.valueOf(250));
        });

        verify(accountMapper, times(1)).incrementBalance(accountId, BigDecimal.valueOf(250));
        verify(accountMapper, times(1)).updateLastModified(eq(accountId), any(OffsetDateTime.class));
    }

    // Verifies that incrementBalance throws ObjectNotFoundException when accountId is null
    @Test
    void testIncrementBalanceThrowsExceptionWhenAccountIdIsNull() {
        assertThrows(ObjectNotFoundException.class, () -> {
            accountService.incrementBalance(null, BigDecimal.valueOf(100));
        });

        verify(accountMapper, never()).incrementBalance(any(UUID.class), any(BigDecimal.class));
    }

    // Verifies that incrementBalance throws ObjectNotProcessedException when balance update fails
    @Test
    void testIncrementBalanceThrowsExceptionWhenBalanceUpdateFails() {
        UUID accountId = UUID.randomUUID();

        when(accountMapper.incrementBalance(accountId, BigDecimal.valueOf(100))).thenReturn(0);

        assertThrows(ObjectNotProcessedException.class, () -> {
            accountService.incrementBalance(accountId, BigDecimal.valueOf(100));
        });

        verify(accountMapper, never()).updateLastModified(any(UUID.class), any(OffsetDateTime.class));
    }

    // Verifies that incrementBalance throws ObjectNotProcessedException when last modified update fails
    @Test
    void testIncrementBalanceThrowsExceptionWhenLastModifiedUpdateFails() {
        UUID accountId = UUID.randomUUID();

        when(accountMapper.incrementBalance(accountId, BigDecimal.valueOf(100))).thenReturn(1);
        when(accountMapper.updateLastModified(eq(accountId), any(OffsetDateTime.class))).thenReturn(0);

        assertThrows(ObjectNotProcessedException.class, () -> {
            accountService.incrementBalance(accountId, BigDecimal.valueOf(100));
        });

        verify(accountMapper, times(1)).incrementBalance(accountId, BigDecimal.valueOf(100));
    }

    // Test decrementBalance method

    // Verifies that decrementBalance successfully decrements account balance and updates last modified timestamp
    @Test
    void testDecrementBalanceSuccessfully() {
        UUID accountId = UUID.randomUUID();

        when(accountMapper.decrementBalance(accountId, BigDecimal.valueOf(150))).thenReturn(1);
        when(accountMapper.updateLastModified(eq(accountId), any(OffsetDateTime.class))).thenReturn(1);

        assertDoesNotThrow(() -> {
            accountService.decrementBalance(accountId, BigDecimal.valueOf(150));
        });

        verify(accountMapper, times(1)).decrementBalance(accountId, BigDecimal.valueOf(150));
        verify(accountMapper, times(1)).updateLastModified(eq(accountId), any(OffsetDateTime.class));
    }

    // Verifies that decrementBalance throws ObjectNotFoundException when accountId is null
    @Test
    void testDecrementBalanceThrowsExceptionWhenAccountIdIsNull() {
        assertThrows(ObjectNotFoundException.class, () -> {
            accountService.decrementBalance(null, BigDecimal.valueOf(100));
        });

        verify(accountMapper, never()).decrementBalance(any(UUID.class), any(BigDecimal.class));
    }

    // Verifies that decrementBalance throws ObjectNotProcessedException when balance update fails
    @Test
    void testDecrementBalanceThrowsExceptionWhenBalanceUpdateFails() {
        UUID accountId = UUID.randomUUID();

        when(accountMapper.decrementBalance(accountId, BigDecimal.valueOf(100))).thenReturn(0);

        assertThrows(ObjectNotProcessedException.class, () -> {
            accountService.decrementBalance(accountId, BigDecimal.valueOf(100));
        });

        verify(accountMapper, never()).updateLastModified(any(UUID.class), any(OffsetDateTime.class));
    }

    // Verifies that decrementBalance throws ObjectNotProcessedException when last modified update fails
    @Test
    void testDecrementBalanceThrowsExceptionWhenLastModifiedUpdateFails() {
        UUID accountId = UUID.randomUUID();

        when(accountMapper.decrementBalance(accountId, BigDecimal.valueOf(100))).thenReturn(1);
        when(accountMapper.updateLastModified(eq(accountId), any(OffsetDateTime.class))).thenReturn(0);

        assertThrows(ObjectNotProcessedException.class, () -> {
            accountService.decrementBalance(accountId, BigDecimal.valueOf(100));
        });

        verify(accountMapper, times(1)).decrementBalance(accountId, BigDecimal.valueOf(100));
    }

    // Test getUserAccounts method

    // Verifies that getUserAccounts successfully retrieves all accounts for a user and maps to AccountResponse
    @Test
    void testGetUserAccountsSuccessfully() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        Account account = new Account();
        account.setAccountId(accountId);
        account.setUserId(userId);
        account.setBalance(BigDecimal.valueOf(1000));
        account.setCurrency("USD");
        account.setStatus("ACTIVE");
        account.setCreatedAt(now);

        List<Account> accounts = List.of(account);

        when(accountMapper.findAllByUserId(userId)).thenReturn(accounts);

        List<AccountResponse> results = accountService.getUserAccounts(userId);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(accountId, results.get(0).getAccountId());
        assertEquals(userId, results.get(0).getUserId());
        assertEquals(BigDecimal.valueOf(1000), results.get(0).getBalance());
        assertEquals("USD", results.get(0).getCurrency());
        assertEquals("ACTIVE", results.get(0).getStatus());
        verify(accountMapper, times(1)).findAllByUserId(userId);
    }

    // Verifies that getUserAccounts returns empty list when user has no accounts
    @Test
    void testGetUserAccountsReturnsEmptyListWhenNoAccounts() {
        UUID userId = UUID.randomUUID();

        when(accountMapper.findAllByUserId(userId)).thenReturn(new ArrayList<>());

        List<AccountResponse> results = accountService.getUserAccounts(userId);

        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(accountMapper, times(1)).findAllByUserId(userId);
    }

    // Verifies that getUserAccounts throws ObjectNotFoundException when userId is null
    @Test
    void testGetUserAccountsThrowsExceptionWhenUserIdIsNull() {
        assertThrows(ObjectNotFoundException.class, () -> {
            accountService.getUserAccounts(null);
        });

        verify(accountMapper, never()).findAllByUserId(any(UUID.class));
    }
}
