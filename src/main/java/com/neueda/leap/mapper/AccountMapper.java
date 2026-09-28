package com.neueda.leap.mapper;

import java.util.UUID;
import java.util.List;

import java.math.BigDecimal;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.neueda.leap.model.Account;

public interface  AccountMapper {
    // insert new account
    @Insert("INSERT INTO accounts (account_id, user_id, balance,status,created_at) " + 
            "VALUES (#{accountId}, #{userId}, #{balance}, #{status}, #{createdAt})")
    void createAccount(Account account);
    
    // Delete account
    @Delete("DELETE FROM accounts WHERE account_id = #{accountId}")
    void deleteAccount(@Param("accountId") UUID accountId);
    
    // find account by account id -> account
    @Select("SELECT * FROM accounts as a WHERE a.account_id = #{accountId}")
    Account findByAccountId(@Param("accountId")UUID accountId);

    // find all accounts for a user -> List<account>
    @Select("SELECT * FROM accounts as a WHERE a.user_id = #{userId}")
    List<Account> findAllByUserId(@Param("userId")UUID userId); 
    
    // find all active accounts -> List<Account>
    @Select("SELECT * FROM accounts as a WHERE a.status = 'ACTIVE'")
    List<Account> findAllActiveAccounts();

    // find account Id by user ID -> UUID
    @Select("SELECT a.account_id FROM accounts as a WHERE a.user_id = #{userId}")
    UUID findAccountIdByUserId(@Param("userId")UUID userId);

    // get account bal -> BigDecimal
    @Select("SELECT a.balance FROM accounts as a WHERE a.account_id = #{accountId}")
    BigDecimal getAccountBalance(@Param("accountId")UUID accountId);

    // find account status by account Id -> String
    @Select("SELECT a.status FROM accounts as a WHERE a.account_id = #{accountId}")
    String findAccountStatusByAccountId(@Param("accountId")UUID accountId);

    // update balance
    @Update("UPDATE accounts as a SET a.balance = #{balance} WHERE a.account_id = #{accountId}")
    void updateBalance(@Param("accountId") UUID accountId,@Param("balance") BigDecimal balance);

    // update status
    @Update("UPDATE accounts as a SET a.status = #{status} WHERE a.account_id = #{accountId}")
    void updateStatus(@Param("accountId") UUID accountId,@Param("status") String status);

    // increment balance
    @Update("UPDATE accounts as a SET a.balance = a.balance + #{amount} WHERE a.account_id = #{accountId}")
    void incrementBalance(@Param("accountId") UUID accountId, @Param("amount") BigDecimal amount);

    // Decrement balance
    @Update("UPDATE accounts as a SET a.balance = a.balance - #{amount} WHERE a.account_id = #{accountId}")
    void decrementBalance(@Param("accountId") UUID accountId, @Param("amount") BigDecimal amount);

    // check if account exists -> boolean
    @Select("SELECT COUNT(*) FROM accounts as a WHERE a.account_id = #{accountId}")
    boolean accountExists(@Param("accountId") UUID accountId);

    // update last modified date and time
    @Update("UPDATE accounts as a SET a.updated_at = #{updatedAt} WHERE a.account_id = #{accountId}")
    void updateLastModified(@Param("accountId") UUID accountId, @Param("updatedAt")LocalDateTime updatedAt);

}
