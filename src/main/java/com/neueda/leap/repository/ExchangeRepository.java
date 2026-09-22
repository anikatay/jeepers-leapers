package com.neueda.leap.repository;

import com.neueda.leap.model.Exchange;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRepository extends JpaRepository<Exchange, String> {

}