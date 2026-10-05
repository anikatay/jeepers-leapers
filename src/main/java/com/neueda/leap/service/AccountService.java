package com.neueda.leap.service;

import com.neueda.leap.dto.response.AccountResponse;
import com.neueda.leap.mapper.AccountMapper;
import com.neueda.leap.model.Account;

import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectNotProcessedException;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import java.time.OffsetDateTime;

import java.util.List;
import java.util.UUID;

@Service
public class AccountService implements IService {

    private final AccountMapper accountMapper;

    public AccountService(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    @Transactional 
    public Account createAccount(Account account){
        if(account == null){
            throw new IllegalArgumentException("Account cannot be null");
        }
        if(account.getUserId() == null){
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if(account.getAccountId() == null){
            account.setAccountId(UUID.randomUUID());
        }
        if(account.getBalance() == null){
            account.setBalance(BigDecimal.ZERO);
        }
        if(account.getCurrency() == null){
            account.setCurrency("USD");
        }
        if(account.getStatus() == null){ 
            account.setStatus("ACTIVE");
        }
        if(account.getCreatedAt() == null){
            account.setCreatedAt(OffsetDateTime.now());
        }
        int accountCreatedFlag = accountMapper.createAccount(account);
        if(accountCreatedFlag < 1){
            throw new ObjectNotProcessedException("Could not create new account");
        }
        return account;
    }

    public Account getAccount(UUID accountId){
        if(accountId == null){
            throw new IllegalArgumentException("Account ID cannot be null");
        }

        Account account = accountMapper.findByAccountId(accountId);
        if(account == null){
            throw new ObjectNotFoundException("Account not found with ID: " + accountId);
        }
        return account;
    }

    @Transactional 
    public void updateBalance(UUID accountId, BigDecimal amount){
        if(accountId == null){
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if(amount == null){
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if(amount.compareTo(BigDecimal.ZERO) < 0){
            throw new IllegalArgumentException("Amount cannot be negative!");
        }
        Account account = getAccount(accountId);
        if(!"ACTIVE".equals(account.getStatus())){
            throw new ObjectInvalidException("Account is not active: " + accountId);
        }

        int balanceUpdateFlag = accountMapper.updateBalance(accountId, amount);
        if(balanceUpdateFlag < 1){
            throw new ObjectNotProcessedException("Was unable to update balance for account " + accountId);
        }
        int modifierUpdateFlag = accountMapper.updateLastModified(accountId, OffsetDateTime.now());
        if(modifierUpdateFlag < 1){
            throw new ObjectNotProcessedException("Was unable to update");
        }
    }

    @Transactional 
    public void closeAccount(UUID accountId){
        if(accountId == null){
            throw new IllegalArgumentException("Account ID cannot be null ");
        }
        int updateStatusFlag = accountMapper.updateStatus(accountId, "CLOSED");
        if(updateStatusFlag < 1){
            throw new ObjectNotProcessedException("Was unable to update status for account " + accountId);
        }
        int modifierUpdateFlag = accountMapper.updateLastModified(accountId, OffsetDateTime.now());
        if(modifierUpdateFlag < 1){
            throw new ObjectNotProcessedException("Was unable to update");
        }
    }

    @Transactional 
    public void incrementBalance(UUID accountId, BigDecimal amount){
        if(accountId == null){
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        int balanceUpdateFlag = accountMapper.incrementBalance(accountId, amount);
        if(balanceUpdateFlag < 1){
            throw new ObjectNotProcessedException("Was unable to update balance for account " + accountId);
        }
        int modifierUpdateFlag = accountMapper.updateLastModified(accountId, OffsetDateTime.now());
        if(modifierUpdateFlag < 1){
            throw new ObjectNotProcessedException("Was unable to update");
        }
    }

    @Transactional 
    public void decrementBalance(UUID accountId, BigDecimal amount){
        if(accountId == null){
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        int balanceUpdateFlag = accountMapper.decrementBalance(accountId, amount);
        if(balanceUpdateFlag < 1){
            throw new ObjectNotProcessedException("Was unable to update balance for account " + accountId);
        }
        int modifierUpdateFlag = accountMapper.updateLastModified(accountId, OffsetDateTime.now());
        if(modifierUpdateFlag < 1){
            throw new ObjectNotProcessedException("Was unable to update");
        }
    }

    public List<AccountResponse> getUserAccounts(UUID userId) {
        if(userId == null){
            throw new IllegalArgumentException("User ID can not be null");
        }
        List<Account> accounts = accountMapper.findAllByUserId(userId);

        return accounts.stream()
                .map(a -> new AccountResponse(
                        a.getAccountId(),
                        a.getUserId(),
                        a.getCurrency(),
                        a.getBalance(),
                        a.getStatus(),
                        a.getCreatedAt()))
                .toList();
    }
}