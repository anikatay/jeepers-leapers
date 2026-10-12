package com.neueda.leap.service;

import com.neueda.leap.dto.response.TradeResponse;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.mapper.TradeMapper;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Holding;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.Trade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

    @Mock
    private TradeMapper tradeMapper;

    @Mock
    private AccountService accountService;

    @Mock
    private InstrumentService instrumentService;

    @Mock
    private HoldingService holdingService;

    @InjectMocks
    private TradeService tradeService;

    // Verifies createTrade rejects a null trade.
    @Test
    void testCreateTradeThrowsWhenTradeIsNull() {
        assertThrows(ObjectNotFoundException.class, () -> tradeService.createTrade(null));

        verify(tradeMapper, never()).createTrade(any(Trade.class));
    }

    // Verifies createTrade rejects missing account id.
    @Test
    void testCreateTradeThrowsWhenAccountIdIsNull() {
        Trade trade = new Trade();
        trade.setInstrumentId(UUID.randomUUID());
        trade.setSide("BUY");
        trade.setQuantity(1);
        trade.setExecutionPrice(BigDecimal.ONE);

        assertThrows(ObjectNotFoundException.class, () -> tradeService.createTrade(trade));

        verify(tradeMapper, never()).createTrade(any(Trade.class));
    }

    // Verifies createTrade rejects missing instrument id.
    @Test
    void testCreateTradeThrowsWhenInstrumentIdIsNull() {
        Trade trade = new Trade();
        trade.setAccountId(UUID.randomUUID());
        trade.setInstrumentId(null);
        trade.setSide("BUY");
        trade.setQuantity(1);
        trade.setExecutionPrice(BigDecimal.ONE);

        assertThrows(ObjectNotFoundException.class, () -> tradeService.createTrade(trade));

        verify(tradeMapper, never()).createTrade(any(Trade.class));
    }

    // Verifies createTrade rejects when instrument service returns null.
    @Test
    void testCreateTradeThrowsWhenInstrumentMissingAfterLookup() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        Trade trade = new Trade();
        trade.setAccountId(accountId);
        trade.setInstrumentId(instrumentId);
        trade.setSide("BUY");
        trade.setQuantity(1);
        trade.setExecutionPrice(BigDecimal.ONE);

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(null);

        assertThrows(ObjectNotFoundException.class, () -> tradeService.createTrade(trade));

        verify(instrumentService, times(1)).getInstrumentById(instrumentId);
        verify(tradeMapper, never()).createTrade(any(Trade.class));
    }

    // Verifies createTrade rejects missing side values.
    @Test
    void testCreateTradeThrowsWhenSideIsNull() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        Trade trade = new Trade();
        trade.setAccountId(accountId);
        trade.setInstrumentId(instrumentId);
        trade.setSide(null);
        trade.setQuantity(1);
        trade.setExecutionPrice(BigDecimal.ONE);

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);

        assertThrows(ObjectNotFoundException.class, () -> tradeService.createTrade(trade));

        verify(tradeMapper, never()).createTrade(any(Trade.class));
    }

    // Verifies createTrade rejects nonpositive quantity values.
    @Test
    void testCreateTradeThrowsWhenQuantityIsInvalid() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        Trade trade = new Trade();
        trade.setAccountId(accountId);
        trade.setInstrumentId(instrumentId);
        trade.setSide("BUY");
        trade.setQuantity(0);
        trade.setExecutionPrice(BigDecimal.ONE);

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);

        assertThrows(ObjectNotFoundException.class, () -> tradeService.createTrade(trade));

        verify(tradeMapper, never()).createTrade(any(Trade.class));
    }

    // Verifies createTrade rejects missing execution price.
    @Test
    void testCreateTradeThrowsWhenExecutionPriceIsNull() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        Trade trade = new Trade();
        trade.setAccountId(accountId);
        trade.setInstrumentId(instrumentId);
        trade.setSide("BUY");
        trade.setQuantity(1);
        trade.setExecutionPrice(null);

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);

        assertThrows(ObjectNotFoundException.class, () -> tradeService.createTrade(trade));

        verify(tradeMapper, never()).createTrade(any(Trade.class));
    }

    // Verifies createTrade fails when mapper cannot persist trade.
    @Test
    void testCreateTradeThrowsWhenMapperCreateFails() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        Trade trade = new Trade();
        trade.setTradeId(UUID.randomUUID());
        trade.setAccountId(accountId);
        trade.setInstrumentId(instrumentId);
        trade.setSide("BUY");
        trade.setQuantity(2);
        trade.setExecutionPrice(BigDecimal.valueOf(10));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        when(tradeMapper.createTrade(trade)).thenReturn(0);

        assertThrows(ObjectNotProcessedException.class, () -> tradeService.createTrade(trade));

        verify(tradeMapper, times(1)).createTrade(trade);
    }

    // Verifies BUY trades create a new holding and decrement account balance.
    @Test
    void testCreateTradeBuyCreatesHoldingAndDecrementsBalance() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        Trade trade = new Trade();
        trade.setTradeId(UUID.randomUUID());
        trade.setAccountId(accountId);
        trade.setInstrumentId(instrumentId);
        trade.setSide("BUY");
        trade.setQuantity(5);
        trade.setExecutionPrice(BigDecimal.valueOf(100));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        Account account = new Account();
        account.setAccountId(accountId);
        account.setBalance(BigDecimal.valueOf(1000));

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        when(tradeMapper.createTrade(trade)).thenReturn(1);
        when(accountService.getAccount(accountId)).thenReturn(account);
        when(holdingService.findHolding(accountId, instrumentId)).thenReturn(null);

        Trade result = tradeService.createTrade(trade);

        assertNotNull(result.getExecutedAt());
        assertEquals(BigDecimal.valueOf(500), result.getTradeValue());
        verify(tradeMapper, times(1)).createTrade(trade);
        verify(holdingService, times(1)).createHoldingInternal(any(Holding.class));
        verify(holdingService, never()).incrementHoldingQuantityImternal(any(Holding.class), any(Integer.class));
        verify(accountService, times(1)).decrementBalance(accountId, BigDecimal.valueOf(500));
    }

    // Verifies BUY trades increment an existing holding.
    @Test
    void testCreateTradeBuyIncrementsExistingHolding() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        Trade trade = new Trade();
        trade.setTradeId(UUID.randomUUID());
        trade.setAccountId(accountId);
        trade.setInstrumentId(instrumentId);
        trade.setSide("BUY");
        trade.setQuantity(3);
        trade.setExecutionPrice(BigDecimal.valueOf(20));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        Account account = new Account();
        account.setAccountId(accountId);
        account.setBalance(BigDecimal.valueOf(500));

        Holding existingHolding = new Holding(accountId, instrumentId, 10);

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        when(tradeMapper.createTrade(trade)).thenReturn(1);
        when(accountService.getAccount(accountId)).thenReturn(account);
        when(holdingService.findHolding(accountId, instrumentId)).thenReturn(existingHolding);

        Trade result = tradeService.createTrade(trade);

        assertEquals(BigDecimal.valueOf(60), result.getTradeValue());
        verify(holdingService, never()).createHoldingInternal(any(Holding.class));
        verify(holdingService, times(1)).incrementHoldingQuantityImternal(existingHolding, 3);
        verify(accountService, times(1)).decrementBalance(accountId, BigDecimal.valueOf(60));
    }

    // Verifies BUY trades fail when account balance is insufficient.
    @Test
    void testCreateTradeBuyThrowsWhenBalanceInsufficient() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        Trade trade = new Trade();
        trade.setTradeId(UUID.randomUUID());
        trade.setAccountId(accountId);
        trade.setInstrumentId(instrumentId);
        trade.setSide("BUY");
        trade.setQuantity(20);
        trade.setExecutionPrice(BigDecimal.valueOf(100));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        Account account = new Account();
        account.setAccountId(accountId);
        account.setBalance(BigDecimal.valueOf(500));

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        when(tradeMapper.createTrade(trade)).thenReturn(1);
        when(accountService.getAccount(accountId)).thenReturn(account);
        when(holdingService.findHolding(accountId, instrumentId)).thenReturn(null);

        assertThrows(ObjectInvalidException.class, () -> tradeService.createTrade(trade));

        verify(accountService, never()).decrementBalance(any(UUID.class), any(BigDecimal.class));
        verify(holdingService, never()).createHoldingInternal(any(Holding.class));
    }

    // Verifies SELL trades decrement holdings and increment account balance.
    @Test
    void testCreateTradeSellDecrementsHoldingAndIncrementsBalance() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        Trade trade = new Trade();
        trade.setTradeId(UUID.randomUUID());
        trade.setAccountId(accountId);
        trade.setInstrumentId(instrumentId);
        trade.setSide("SELL");
        trade.setQuantity(4);
        trade.setExecutionPrice(BigDecimal.valueOf(50));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        Holding existingHolding = new Holding(accountId, instrumentId, 10);

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        when(tradeMapper.createTrade(trade)).thenReturn(1);
        when(holdingService.findHolding(accountId, instrumentId)).thenReturn(existingHolding);

        Trade result = tradeService.createTrade(trade);

        assertEquals(BigDecimal.valueOf(200), result.getTradeValue());
        verify(holdingService, times(1)).decrementHoldingQuantityImternal(existingHolding, 4);
        verify(accountService, times(1)).incrementBalance(accountId, BigDecimal.valueOf(200));
    }

    // Verifies SELL trades fail when holding does not exist.
    @Test
    void testCreateTradeSellThrowsWhenHoldingMissing() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        Trade trade = new Trade();
        trade.setTradeId(UUID.randomUUID());
        trade.setAccountId(accountId);
        trade.setInstrumentId(instrumentId);
        trade.setSide("SELL");
        trade.setQuantity(2);
        trade.setExecutionPrice(BigDecimal.valueOf(80));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        when(tradeMapper.createTrade(trade)).thenReturn(1);
        when(holdingService.findHolding(accountId, instrumentId)).thenReturn(null);

        assertThrows(ObjectInvalidException.class, () -> tradeService.createTrade(trade));

        verify(holdingService, never()).decrementHoldingQuantityImternal(any(Holding.class), any(Integer.class));
        verify(accountService, never()).incrementBalance(any(UUID.class), any(BigDecimal.class));
    }

    // Verifies SELL trades fail when quantity exceeds holding amount.
    @Test
    void testCreateTradeSellThrowsWhenQuantityExceedsHolding() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        Trade trade = new Trade();
        trade.setTradeId(UUID.randomUUID());
        trade.setAccountId(accountId);
        trade.setInstrumentId(instrumentId);
        trade.setSide("SELL");
        trade.setQuantity(20);
        trade.setExecutionPrice(BigDecimal.valueOf(80));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        Holding existingHolding = new Holding(accountId, instrumentId, 5);

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        when(tradeMapper.createTrade(trade)).thenReturn(1);
        when(holdingService.findHolding(accountId, instrumentId)).thenReturn(existingHolding);

        assertThrows(ObjectInvalidException.class, () -> tradeService.createTrade(trade));

        verify(holdingService, never()).decrementHoldingQuantityImternal(any(Holding.class), any(Integer.class));
        verify(accountService, never()).incrementBalance(any(UUID.class), any(BigDecimal.class));
    }

    // Verifies createTrade returns trade for unsupported side values.
    @Test
    void testCreateTradeReturnsTradeForUnsupportedSide() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        Trade trade = new Trade();
        trade.setTradeId(UUID.randomUUID());
        trade.setAccountId(accountId);
        trade.setInstrumentId(instrumentId);
        trade.setSide("HOLD");
        trade.setQuantity(1);
        trade.setExecutionPrice(BigDecimal.valueOf(10));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        when(tradeMapper.createTrade(trade)).thenReturn(1);

        Trade result = tradeService.createTrade(trade);

        assertNotNull(result);
        assertEquals("HOLD", result.getSide());
        verify(accountService, never()).decrementBalance(any(UUID.class), any(BigDecimal.class));
        verify(accountService, never()).incrementBalance(any(UUID.class), any(BigDecimal.class));
    }

    // Verifies getTradesByAccountAndInstrument returns mapper results.
    @Test
    void testGetTradesByAccountAndInstrumentReturnsMapperResults() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        List<TradeResponse> expected = List.of();

        when(tradeMapper.findTradesByAccountAndInstrument(accountId, instrumentId)).thenReturn(expected);

        List<TradeResponse> result = tradeService.getTradesByAccountAndInstrument(accountId, instrumentId);

        assertEquals(expected, result);
        verify(tradeMapper, times(1)).findTradesByAccountAndInstrument(accountId, instrumentId);
    }

    // Verifies getTradesByAccountAndInstrument rejects null account ids.
    @Test
    void testGetTradesByAccountAndInstrumentThrowsWhenAccountIdNull() {
        assertThrows(ObjectNotFoundException.class,
                () -> tradeService.getTradesByAccountAndInstrument(null, UUID.randomUUID()));

        verify(tradeMapper, never()).findTradesByAccountAndInstrument(any(UUID.class), any(UUID.class));
    }

    // Verifies getTradesByAccountAndInstrument rejects null instrument ids.
    @Test
    void testGetTradesByAccountAndInstrumentThrowsWhenInstrumentIdNull() {
        assertThrows(ObjectNotFoundException.class,
                () -> tradeService.getTradesByAccountAndInstrument(UUID.randomUUID(), null));

        verify(tradeMapper, never()).findTradesByAccountAndInstrument(any(UUID.class), any(UUID.class));
    }

    // Verifies getAllTradesByAccountWithInstrument returns mapper results.
    @Test
    void testGetAllTradesByAccountWithInstrumentReturnsMapperResults() {
        UUID accountId = UUID.randomUUID();
        List<TradeResponse> expected = List.of();

        when(tradeMapper.findAllTradesForAccountWithInstrument(accountId)).thenReturn(expected);

        List<TradeResponse> result = tradeService.getAllTradesByAccountWithInstrument(accountId);

        assertEquals(expected, result);
        verify(tradeMapper, times(1)).findAllTradesForAccountWithInstrument(accountId);
    }

    // Verifies getAllTradesByAccountWithInstrument rejects null account ids.
    @Test
    void testGetAllTradesByAccountWithInstrumentThrowsWhenAccountIdNull() {
        assertThrows(ObjectNotFoundException.class,
                () -> tradeService.getAllTradesByAccountWithInstrument(null));

        verify(tradeMapper, never()).findAllTradesForAccountWithInstrument(any(UUID.class));
    }
}
