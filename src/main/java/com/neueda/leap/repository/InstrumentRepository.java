package com.neueda.leap.repository;

import com.neueda.leap.model.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface InstrumentRepository extends JpaRepository<Instrument, UUID> {

    Optional<Instrument> findByTicker(String ticker);        // lookup by AAPL/TSLA/etc
    List<Instrument> findByExchangeExchangeId(String exchangeId);
}