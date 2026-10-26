package com.neueda.leap.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.dto.request.AccountRequest;
import com.neueda.leap.dto.request.UpdateBalanceRequest;
import com.neueda.leap.dto.response.AccountResponse;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.model.Account;
import com.neueda.leap.service.AccountService;
import com.neueda.leap.service.AuthorizationService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@WebMvcTest(AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountService accountService;

        @MockBean
        private AuthorizationService authorizationService;

    // Checks that getting an existing account returns 200 OK with account details.
    @Test
    void testGetAccountReturnsAccountWithValidId() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        
        Account account = new Account();
        account.setAccountId(accountId);
        account.setUserId(userId);
        account.setBalance(BigDecimal.valueOf(1000));
        account.setCurrency("USD");
        account.setStatus("ACTIVE");
        account.setCreatedAt(OffsetDateTime.now());

        when(accountService.getAccount(accountId)).thenReturn(account);

        mockMvc.perform(get("/api/accounts/{accountId}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()));
    }

    // Checks that getting a nonexistent account returns 404 NOT FOUND with error code.
    @Test
    void testGetAccountReturnsNotFoundForNonexistentAccount() throws Exception {
        UUID accountId = UUID.randomUUID();
        
        when(accountService.getAccount(accountId))
                .thenThrow(new ObjectNotFoundException("Account not found with ID: " + accountId));

        mockMvc.perform(get("/api/accounts/{accountId}", accountId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

        // Checks that getting an account with an invalid UUID path returns 500 INTERNAL_SERVER_ERROR.
        @Test
        void testGetAccountReturnsInternalServerErrorForInvalidUuid() throws Exception {
                mockMvc.perform(get("/api/accounts/{accountId}", "not-a-uuid"))
                                .andExpect(status().isInternalServerError())
                                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        }

    // Checks that creating an account with valid data returns 201 CREATED.
    @Test
    void testCreateAccountSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        BigDecimal initialBalance = BigDecimal.valueOf(5000);
        String currency = "EUR";
        
        AccountRequest request = new AccountRequest(userId, initialBalance, currency);
        
        UUID accountId = UUID.randomUUID();
        Account createdAccount = new Account();
        createdAccount.setAccountId(accountId);
        createdAccount.setUserId(userId);
        createdAccount.setBalance(initialBalance);
        createdAccount.setCurrency(currency);
        createdAccount.setStatus("ACTIVE");
        createdAccount.setCreatedAt(OffsetDateTime.now());

        when(accountService.createAccount(any(Account.class))).thenReturn(createdAccount);

        mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.balance").value(5000.0))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    // Checks that creating an account with missing userId returns 404 NOT FOUND from service validation.
    @Test
    void testCreateAccountFailsWithValidationError() throws Exception {
        // Missing userId (null)
        AccountRequest request = new AccountRequest(null, BigDecimal.valueOf(1000), "USD");

        when(accountService.createAccount(any(Account.class)))
                .thenThrow(new ObjectNotFoundException("User ID cannot be null"));

        mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // Checks that creating an account with null balance and currency still returns 201 CREATED.
    @Test
    void testCreateAccountWithNullBalanceAndCurrencyStillSucceeds() throws Exception {
        UUID userId = UUID.randomUUID();
        AccountRequest request = new AccountRequest(userId, null, null);

        UUID accountId = UUID.randomUUID();
        Account createdAccount = new Account();
        createdAccount.setAccountId(accountId);
        createdAccount.setUserId(userId);
        createdAccount.setBalance(BigDecimal.ZERO);
        createdAccount.setCurrency("USD");
        createdAccount.setStatus("ACTIVE");
        createdAccount.setCreatedAt(OffsetDateTime.now());

        when(accountService.createAccount(any(Account.class))).thenReturn(createdAccount);

        mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.balance").value(0.0))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    // Checks that creating an account returns 500 INTERNAL_SERVER_ERROR for unhandled exceptions.
    @Test
    void testCreateAccountReturnsInternalServerErrorForUnhandledException() throws Exception {
        UUID userId = UUID.randomUUID();
        AccountRequest request = new AccountRequest(userId, BigDecimal.valueOf(1000), "USD");

        when(accountService.createAccount(any(Account.class)))
                .thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    // Checks that closing an existing account returns 204 NO_CONTENT.
    @Test
    void testCloseAccountSuccessfully() throws Exception {
        UUID accountId = UUID.randomUUID();

        doNothing().when(accountService).closeAccount(accountId);

        mockMvc.perform(delete("/api/accounts/{accountId}", accountId))
                .andExpect(status().isNoContent());

        verify(accountService).closeAccount(accountId);
    }

    // Checks that closing an account throws an unhandled exception returns 500 INTERNAL_SERVER_ERROR.
    @Test
    void testCloseAccountThrowsUnhandledExceptionFor500Error() throws Exception {
        UUID accountId = UUID.randomUUID();

        doThrow(new ObjectNotProcessedException("Was unable to update status for account " + accountId))
                .when(accountService).closeAccount(accountId);

        mockMvc.perform(delete("/api/accounts/{accountId}", accountId))
                .andExpect(status().isInternalServerError());
    }

    // Checks that closing a nonexistent account returns 404 NOT_FOUND.
    @Test
    void testCloseAccountReturnsNotFoundWhenAccountMissing() throws Exception {
        UUID accountId = UUID.randomUUID();

        doThrow(new ObjectNotFoundException("Account not found with ID: " + accountId))
                .when(accountService).closeAccount(accountId);

        mockMvc.perform(delete("/api/accounts/{accountId}", accountId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // Checks that depositing a valid amount into an account returns 204 NO_CONTENT.
    @Test
    void testDepositSuccessfully() throws Exception {
        UUID accountId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(500);
        
        UpdateBalanceRequest request = new UpdateBalanceRequest(amount);

        doNothing().when(accountService).incrementBalance(accountId, amount);

        mockMvc.perform(patch("/api/accounts/{accountId}/deposit", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(accountService).incrementBalance(accountId, amount);
    }

    // Checks that depositing a negative amount returns 404 NOT FOUND from service validation.
    @Test
    void testDepositFailsWithValidationError() throws Exception {
        UUID accountId = UUID.randomUUID();
        
        // Negative amount (invalid)
        UpdateBalanceRequest request = new UpdateBalanceRequest(BigDecimal.valueOf(-500));

        doThrow(new ObjectNotFoundException("Amount cannot be negative!"))
                .when(accountService).incrementBalance(accountId, BigDecimal.valueOf(-500));

        mockMvc.perform(patch("/api/accounts/{accountId}/deposit", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // Checks that deposit throws an unhandled exception returns 500 INTERNAL_SERVER_ERROR.
    @Test
    void testDepositThrowsUnhandledExceptionFor500Error() throws Exception {
        UUID accountId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(500);
        
        UpdateBalanceRequest request = new UpdateBalanceRequest(amount);

        doThrow(new ObjectNotProcessedException("Was unable to update balance for account " + accountId))
                .when(accountService).incrementBalance(accountId, amount);

        mockMvc.perform(patch("/api/accounts/{accountId}/deposit", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

        // Checks that depositing with an invalid accountId path returns 500 INTERNAL_SERVER_ERROR.
        @Test
        void testDepositReturnsInternalServerErrorForInvalidUuid() throws Exception {
                UpdateBalanceRequest request = new UpdateBalanceRequest(BigDecimal.valueOf(50));

                mockMvc.perform(patch("/api/accounts/{accountId}/deposit", "not-a-uuid")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isInternalServerError())
                                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        }

    // Checks that withdrawing a valid amount from an account returns 204 NO_CONTENT.
    @Test
    void testWithdrawSuccessfully() throws Exception {
        UUID accountId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(200);
        
        UpdateBalanceRequest request = new UpdateBalanceRequest(amount);

        doNothing().when(accountService).decrementBalance(accountId, amount);

        mockMvc.perform(patch("/api/accounts/{accountId}/withdraw", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(accountService).decrementBalance(accountId, amount);
    }

    // Checks that withdrawing with null amount returns 500 INTERNAL_SERVER_ERROR from unhandled exception.
    @Test
    void testWithdrawFailsWithValidationError() throws Exception {
        UUID accountId = UUID.randomUUID();
        
        // Missing amount (null)
        UpdateBalanceRequest request = new UpdateBalanceRequest(null);

        doThrow(new ObjectNotProcessedException("Was unable to update balance for account " + accountId))
                .when(accountService).decrementBalance(accountId, null);

        mockMvc.perform(patch("/api/accounts/{accountId}/withdraw", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    // Checks that withdraw throws an unhandled exception returns 500 INTERNAL_SERVER_ERROR.
    @Test
    void testWithdrawThrowsUnhandledExceptionFor500Error() throws Exception {
        UUID accountId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(200);
        
        UpdateBalanceRequest request = new UpdateBalanceRequest(amount);

        doThrow(new ObjectNotProcessedException("Was unable to update balance for account " + accountId))
                .when(accountService).decrementBalance(accountId, amount);

        mockMvc.perform(patch("/api/accounts/{accountId}/withdraw", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

        // Checks that getAllActiveAccounts returns 200 OK with a list of accounts.
        @Test
        void testGetAllActiveAccountsReturnsOk() throws Exception {
                UUID accountId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                AccountResponse response = new AccountResponse(
                                accountId,
                                userId,
                                "USD",
                                BigDecimal.valueOf(1000),
                                "ACTIVE",
                                OffsetDateTime.now()
                );

                when(accountService.getAllActiveAccounts()).thenReturn(List.of(response));

                mockMvc.perform(get("/api/accounts/active"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].accountId").value(accountId.toString()))
                                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
        }

        // Checks that getAllActiveAccounts returns 200 OK with an empty list when no accounts exist.
        @Test
        void testGetAllActiveAccountsReturnsOkWithEmptyList() throws Exception {
                when(accountService.getAllActiveAccounts()).thenReturn(List.of());

                mockMvc.perform(get("/api/accounts/active"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray());
        }

        // Checks that getAllActiveAccounts returns 500 INTERNAL_SERVER_ERROR for unhandled exceptions.
        @Test
        void testGetAllActiveAccountsReturnsInternalServerError() throws Exception {
                when(accountService.getAllActiveAccounts()).thenThrow(new RuntimeException("unexpected"));

                mockMvc.perform(get("/api/accounts/active"))
                                .andExpect(status().isInternalServerError())
                                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        }
}
