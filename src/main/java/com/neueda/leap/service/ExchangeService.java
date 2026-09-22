package com.neueda.leap.service;

import org.springframework.stereotype.Service;

import com.neueda.leap.dto.ExchangeResponse;
import com.neueda.leap.model.Exchange;
import com.neueda.leap.repository.ExchangeRepository;

import java.util.List;
import java.util.Optional;

@Service
public class ExchangeService implements IService {

    private final ExchangeRepository exchangeRepository;

    public ExchangeService(ExchangeRepository exchangeRepository) {
        this.exchangeRepository = exchangeRepository;
    }

    public List<ExchangeResponse> getAllExchanges() {
        return exchangeRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public Optional<ExchangeResponse> getByExchangeId(String exchangeId) {
        return exchangeRepository.findById(exchangeId)
                .map(this::toDto);
    }

    private ExchangeResponse toDto(Exchange e) {
        return new ExchangeResponse(
                e.getExchangeId(),
                e.getName(),
                e.getRegion(),
                e.getTimezone(),
                e.getCurrency());
    }
}
