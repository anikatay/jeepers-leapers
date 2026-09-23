package com.neueda.leap.repository;

import com.neueda.leap.model.Holding;
import com.neueda.leap.model.HoldingId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface HoldingRepository extends JpaRepository<Holding, HoldingId> {

    /**
     * Find all holdings for a given user, across all of that user's accounts.
     * Holdings link to accounts, accounts link to users — so this needs an
     * explicit join rather than a simple derived-name query.
     */
    @Query("SELECT h FROM Holding h WHERE h.account.user.userId = :userId")
    List<Holding> findByAccountUserId(@Param("userId") UUID userId);

    /**
     * Find all holdings for one specific account (useful if you ever need
     * per-account views rather than the aggregated cross-account portfolio).
     */
    @Query("SELECT h FROM Holding h WHERE h.account.accountId = :accountId")
    List<Holding> findByAccountId(@Param("accountId") UUID accountId);
}