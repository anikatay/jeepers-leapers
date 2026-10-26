package com.neueda.leap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.model.Account;
import com.neueda.leap.service.AccountService;
import com.neueda.leap.service.AdminDashboardService;
import com.neueda.leap.service.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminDashboardService adminDashboardService;

    @MockBean
    private AuthorizationService authorizationService;

    @MockBean
    private AccountService accountService;

    @Test
    void testFreezeAccountRequiresReasonAndReturnsFrozenAccount() throws Exception {
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        account.setAccountId(accountId);
        account.setUserId(UUID.randomUUID());
        account.setStatus("FROZEN");
        account.setFreezeReason("Suspicious trading activity");
        when(accountService.freezeAccount(eq(accountId), eq("Suspicious trading activity")))
                .thenReturn(account);

        mockMvc.perform(patch("/api/admin/accounts/{accountId}/freeze", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("reason", "Suspicious trading activity"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"))
                .andExpect(jsonPath("$.freezeReason").value("Suspicious trading activity"));
    }

    @Test
    void testFreezeAccountRejectsBlankReason() throws Exception {
        mockMvc.perform(patch("/api/admin/accounts/{accountId}/freeze", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void testUnfreezeAccountUsesPatchAndReturnsActiveAccount() throws Exception {
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        account.setAccountId(accountId);
        account.setUserId(UUID.randomUUID());
        account.setStatus("ACTIVE");
        when(accountService.unfreezeAccount(eq(accountId))).thenReturn(account);

        mockMvc.perform(patch("/api/admin/accounts/{accountId}/unfreeze", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}