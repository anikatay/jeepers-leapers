package com.neueda.leap.service;

import org.springframework.stereotype.Service;

import com.neueda.leap.dto.request.ExchangeRequest;
import com.neueda.leap.dto.response.ExchangeResponse;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.mapper.ExchangeMapper;
import com.neueda.leap.model.Exchange;

import java.util.List;

@Service
public class ExchangeService implements IService {

    private final ExchangeMapper exchangeMapper;

    public ExchangeService(ExchangeMapper exchangeMapper) {
        this.exchangeMapper = exchangeMapper;
    }

    public List<ExchangeResponse> getAllExchanges() {
        return exchangeMapper.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public ExchangeResponse getExchangeById(String exchangeId) {
        return exchangeMapper.findById(exchangeId)
                .map(this::toResponse)
                .orElseThrow(() -> new ObjectNotFoundException("Exchange not found: " + exchangeId));
    }

    public ExchangeResponse createExchange(ExchangeRequest request) {
        Exchange exchange = new Exchange(
                request.exchangeId(),
                request.name(),
                request.region(),
                request.timezone(),
                request.currency()
        );
        exchangeMapper.insert(exchange);
        return toResponse(exchange);
    }

    public ExchangeResponse updateExchange(String exchangeId, ExchangeRequest request) {
        // Verify exchange exists
        exchangeMapper.findById(exchangeId)
                .orElseThrow(() -> new ObjectNotFoundException("Exchange not found: " + exchangeId));

        Exchange exchange = new Exchange(
                exchangeId,
                request.name(),
                request.region(),
                request.timezone(),
                request.currency()
        );
        exchangeMapper.update(exchange);
        return toResponse(exchange);
    }

    public void deleteExchange(String exchangeId) {
        // Verify exchange exists
        exchangeMapper.findById(exchangeId)
                .orElseThrow(() -> new ObjectNotFoundException("Exchange not found: " + exchangeId));

        exchangeMapper.deleteById(exchangeId);
    }

    private ExchangeResponse toResponse(Exchange exchange) {
        return new ExchangeResponse(
                exchange.getExchangeId(),
                exchange.getName(),
                exchange.getRegion(),
                exchange.getTimezone(),
                exchange.getCurrency()
        );
    }
}
