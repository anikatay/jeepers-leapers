package com.neueda.leap.service;


import com.neueda.leap.dto.AccountResponse;
import com.neueda.leap.model.Account;
import com.neueda.leap.repository.AccountRepository;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }


    public List<AccountResponse> getAccountsForUser(UUID userId) {
        List<Account> accounts = accountRepository.findByUserUserId(userId);

        List<AccountResponse> response = accounts.stream()
                .map(a -> new AccountResponse(
                        a.getAccountId(),
                        a.getCurrency(),
                        a.getBalance(),
                        a.getStatus(),
                        a.getCreatedAt()))
                .toList();

        return response;
    }
}