package com.neueda.leap.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import com.neueda.leap.model.Account;

@Service
public class AuthorizationService {

    private final AccountService accountService;

    public AuthorizationService(AccountService accountService) {
        this.accountService = accountService;
    }

    public UUID getAuthenticatedUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AccessDeniedException("Authenticated user is required");
        }

        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Authenticated user is invalid");
        }
    }

    public void requireAccountOwner(UUID accountId, UUID authenticatedUserId) {
        if (accountId == null || authenticatedUserId == null) {
            throw new AccessDeniedException("Authenticated user and account are required");
        }

        Account account = accountService.getAccount(accountId);

        if (!authenticatedUserId.equals(account.getUserId())) {
            throw new AccessDeniedException("You cannot access this account");
        }
    }

    public void requireUser(UUID requestedUserId, UUID authenticatedUserId) {
        if (requestedUserId == null || authenticatedUserId == null
                || !authenticatedUserId.equals(requestedUserId)) {
            throw new AccessDeniedException("You cannot access this user");
        }
    }
}
