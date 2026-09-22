package com.neueda.leap.repository;

import com.neueda.leap.model.Exchange;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ExchangeRepository extends JpaRepository<Exchange, String> {

    Optional<Exchange> findByName(String name);
    List<Exchange> findByRegion(String region);
    List<Exchange> findByTimezone(String timezone);
    List<Exchange> findByCurrency(String currency);
}
