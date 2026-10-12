package com.neueda.leap.service;

import com.neueda.leap.dto.request.ExchangeRequest;
import com.neueda.leap.dto.response.ExchangeResponse;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.mapper.ExchangeMapper;
import com.neueda.leap.model.Exchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeServiceTest {

    @Mock
    private ExchangeMapper exchangeMapper;

    @InjectMocks
    private ExchangeService exchangeService;

    // Verifies getAllExchanges returns mapped exchange responses.
    @Test
    void testGetAllExchangesReturnsMappedResponses() {
        Exchange nyse = new Exchange("XNYS", "New York Stock Exchange", "US", "America/New_York", "USD");
        Exchange lse = new Exchange("XLON", "London Stock Exchange", "UK", "Europe/London", "GBP");

        when(exchangeMapper.findAll()).thenReturn(List.of(nyse, lse));

        List<ExchangeResponse> responses = exchangeService.getAllExchanges();

        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals("XNYS", responses.get(0).exchangeId());
        assertEquals("London Stock Exchange", responses.get(1).name());
        verify(exchangeMapper, times(1)).findAll();
    }

    // Verifies getAllExchanges propagates mapper errors.
    @Test
    void testGetAllExchangesPropagatesMapperException() {
        when(exchangeMapper.findAll()).thenThrow(new RuntimeException("database failure"));

        assertThrows(RuntimeException.class, () -> exchangeService.getAllExchanges());

        verify(exchangeMapper, times(1)).findAll();
    }

    // Verifies getExchangeById returns a mapped response when found.
    @Test
    void testGetExchangeByIdReturnsResponseWhenFound() {
        Exchange exchange = new Exchange("XNYS", "New York Stock Exchange", "US", "America/New_York", "USD");

        when(exchangeMapper.findById("XNYS")).thenReturn(Optional.of(exchange));

        ExchangeResponse response = exchangeService.getExchangeById("XNYS");

        assertNotNull(response);
        assertEquals("XNYS", response.exchangeId());
        assertEquals("US", response.region());
        verify(exchangeMapper, times(1)).findById("XNYS");
    }

    // Verifies getExchangeById throws ObjectNotFoundException when missing.
    @Test
    void testGetExchangeByIdThrowsWhenNotFound() {
        when(exchangeMapper.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThrows(ObjectNotFoundException.class, () -> exchangeService.getExchangeById("UNKNOWN"));

        verify(exchangeMapper, times(1)).findById("UNKNOWN");
    }

    // Verifies createExchange inserts and returns the created exchange response.
    @Test
    void testCreateExchangeReturnsCreatedResponse() {
        ExchangeRequest request = new ExchangeRequest("XNYS", "New York Stock Exchange", "US", "America/New_York", "USD");

        ExchangeResponse response = exchangeService.createExchange(request);

        assertNotNull(response);
        assertEquals("XNYS", response.exchangeId());
        assertEquals("New York Stock Exchange", response.name());
        verify(exchangeMapper, times(1)).insert(org.mockito.ArgumentMatchers.any(Exchange.class));
    }

    // Verifies createExchange propagates mapper insert errors.
    @Test
    void testCreateExchangePropagatesMapperException() {
        ExchangeRequest request = new ExchangeRequest("XNYS", "New York Stock Exchange", "US", "America/New_York", "USD");
        doThrow(new RuntimeException("insert failed")).when(exchangeMapper).insert(org.mockito.ArgumentMatchers.any(Exchange.class));

        assertThrows(RuntimeException.class, () -> exchangeService.createExchange(request));

        verify(exchangeMapper, times(1)).insert(org.mockito.ArgumentMatchers.any(Exchange.class));
    }

    // Verifies updateExchange updates and returns the updated exchange response.
    @Test
    void testUpdateExchangeReturnsUpdatedResponse() {
        ExchangeRequest request = new ExchangeRequest("IGNORED", "NYSE Updated", "US", "America/New_York", "USD");
        Exchange existing = new Exchange("XNYS", "Old Name", "US", "America/New_York", "USD");

        when(exchangeMapper.findById("XNYS")).thenReturn(Optional.of(existing));

        ExchangeResponse response = exchangeService.updateExchange("XNYS", request);

        assertNotNull(response);
        assertEquals("XNYS", response.exchangeId());
        assertEquals("NYSE Updated", response.name());
        verify(exchangeMapper, times(1)).findById("XNYS");
        verify(exchangeMapper, times(1)).update(org.mockito.ArgumentMatchers.any(Exchange.class));
    }

    // Verifies updateExchange throws ObjectNotFoundException for missing exchange.
    @Test
    void testUpdateExchangeThrowsWhenNotFound() {
        ExchangeRequest request = new ExchangeRequest("XNYS", "NYSE", "US", "America/New_York", "USD");

        when(exchangeMapper.findById("XNYS")).thenReturn(Optional.empty());

        assertThrows(ObjectNotFoundException.class, () -> exchangeService.updateExchange("XNYS", request));

        verify(exchangeMapper, times(1)).findById("XNYS");
    }

    // Verifies updateExchange propagates mapper update errors.
    @Test
    void testUpdateExchangePropagatesMapperException() {
        ExchangeRequest request = new ExchangeRequest("XNYS", "NYSE", "US", "America/New_York", "USD");
        Exchange existing = new Exchange("XNYS", "Old Name", "US", "America/New_York", "USD");

        when(exchangeMapper.findById("XNYS")).thenReturn(Optional.of(existing));
        doThrow(new RuntimeException("update failed")).when(exchangeMapper).update(org.mockito.ArgumentMatchers.any(Exchange.class));

        assertThrows(RuntimeException.class, () -> exchangeService.updateExchange("XNYS", request));

        verify(exchangeMapper, times(1)).findById("XNYS");
        verify(exchangeMapper, times(1)).update(org.mockito.ArgumentMatchers.any(Exchange.class));
    }

    // Verifies deleteExchange removes an existing exchange.
    @Test
    void testDeleteExchangeDeletesWhenFound() {
        Exchange existing = new Exchange("XNYS", "New York Stock Exchange", "US", "America/New_York", "USD");

        when(exchangeMapper.findById("XNYS")).thenReturn(Optional.of(existing));

        exchangeService.deleteExchange("XNYS");

        verify(exchangeMapper, times(1)).findById("XNYS");
        verify(exchangeMapper, times(1)).deleteById("XNYS");
    }

    // Verifies deleteExchange throws ObjectNotFoundException for missing exchange.
    @Test
    void testDeleteExchangeThrowsWhenNotFound() {
        when(exchangeMapper.findById("XNYS")).thenReturn(Optional.empty());

        assertThrows(ObjectNotFoundException.class, () -> exchangeService.deleteExchange("XNYS"));

        verify(exchangeMapper, times(1)).findById("XNYS");
    }

    // Verifies deleteExchange propagates mapper delete errors.
    @Test
    void testDeleteExchangePropagatesMapperException() {
        Exchange existing = new Exchange("XNYS", "New York Stock Exchange", "US", "America/New_York", "USD");

        when(exchangeMapper.findById("XNYS")).thenReturn(Optional.of(existing));
        doThrow(new RuntimeException("delete failed")).when(exchangeMapper).deleteById("XNYS");

        assertThrows(RuntimeException.class, () -> exchangeService.deleteExchange("XNYS"));

        verify(exchangeMapper, times(1)).findById("XNYS");
        verify(exchangeMapper, times(1)).deleteById("XNYS");
    }
}
