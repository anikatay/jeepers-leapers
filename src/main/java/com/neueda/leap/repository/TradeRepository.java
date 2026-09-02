package com.neueda.leap.repository;

import com.neueda.leap.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TradeRepository extends JpaRepository<Trade, UUID> {
    // findAll() inherited — returns all trades with eagerly-fetched user and instrument
}

