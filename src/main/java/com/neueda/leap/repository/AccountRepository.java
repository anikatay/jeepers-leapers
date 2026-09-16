package com.neueda.leap.repository;

import com.neueda.leap.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUserUserId(UUID userId);
    List<Account> findByStatus(String status);
}