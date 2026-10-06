package com.neueda.leap.repository;

import com.neueda.leap.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TradeRepository extends JpaRepository<Trade, UUID> {

    /**
     * Find all trades for a given user, across all of that user's accounts.
     */
    List<Trade> findByAccountUserUserId(UUID userId);

    /**
     * Find all trades for one specific account.
     */
    List<Trade> findByAccountAccountId(UUID accountId);
}