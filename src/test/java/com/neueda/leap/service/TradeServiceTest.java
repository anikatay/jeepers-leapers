package com.neueda.leap.service;

import com.neueda.leap.dto.response.TradeResponse;
import com.neueda.leap.exception.ApplicationException;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.mapper.TradeMapper;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Holding;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.Trade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TradeServiceTest {

    @Mock
    private TradeMapper tradeMapper;

    @Mock
    private AccountService accountService;

    @Mock
    private InstrumentService instrumentService;

    @Mock
    private HoldingService holdingService;

    @Mock
    private MarketDataService marketDataService;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private MarketSessionService marketSessionService;

    @InjectMocks
    private TradeService tradeService;

    private final UUID authenticatedUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(instrumentService.refreshPriceIfStale(any(UUID.class)))
                .thenAnswer(invocation -> {
                    Instrument instrument = instrumentService.getInstrumentById(invocation.getArgument(0));
                    if (instrument != null && instrument.getUpdatedAt() == null) {
                        instrument.setUpdatedAt(OffsetDateTime.now());
                    }
                    if (instrument != null && instrument.getCurrentPrice() == null) {
                        instrument.setCurrentPrice(BigDecimal.valueOf(100));
                    }
                    return instrument;
                });
        lenient().when(accountService.getAccount(any(UUID.class))).thenReturn(activeAccount());
        lenient().when(accountService.getAccountForTrade(any(UUID.class))).thenReturn(activeAccount());
        lenient().when(marketDataService.getExchangeRate(any(String.class), any(String.class)))
                .thenReturn(BigDecimal.ONE);
        lenient().doNothing().when(authorizationService)
            .requireAccountOwner(any(UUID.class), any(UUID.class));
        ReflectionTestUtils.setField(tradeService, "maxPriceAgeSeconds", 180L);
        lenient().doAnswer(invocation -> {
            if (invocation.getArgument(0) == null) {
                throw new ObjectNotFoundException("Instrument not found");
            }
            return null;
        }).when(marketSessionService).assertMarketOpen(nullable(Instrument.class));
    }

    private Account activeAccount() {
        Account account = new Account();
        account.setAccountId(UUID.randomUUID());
        account.setStatus("ACTIVE");
        account.setBalance(BigDecimal.valueOf(1_000_000));
        return account;
    }

    // Verifies createTrade rejects a null trade.
    @Test
    void testCreateTradeThrowsWhenTradeIsNull() {
        assertThrows(ObjectInvalidException.class, () -> tradeService.createTrade(null, authenticatedUserId));

        verify(tradeMapper, never()).createTrade(any(Trade.class));
    }

    @Test
    void testCreateTradeRejectsFrozenAccountBeforeInstrumentLookup() {
        UUID accountId = UUID.randomUUID();
        Trade trade = new Trade();
        trade.setAccountId(accountId);
        trade.setInstrumentId(UUID.randomUUID());
        trade.setSide("BUY");
        trade.setQuantity(BigDecimal.ONE);

        Account frozenAccount = activeAccount();
        frozenAccount.setAccountId(accountId);
        frozenAccount.setStatus("FROZEN");
        frozenAccount.setFreezeReason("Suspicious trading activity");
        when(accountService.getAccountForTrade(accountId)).thenReturn(frozenAccount);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> tradeService.createTrade(trade, authenticatedUserId));

        assertEquals("ACCOUNT_FROZEN", exception.getCode());
        assertEquals("The account was frozen by an administrator: Suspicious trading activity",
                exception.getDetails());
        verify(instrumentService, never()).getInstrumentById(any(UUID.class));
        verify(tradeMapper, never()).createTrade(any(Trade.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    // Verifies createTrade rejects missing account id.
    @Test
    void testCreateTradeThrowsWhenAccountIdIsNull() {
        Trade trade = new Trade();
        trade.setInstrumentId(UUID.randomUUID());
        trade.setSide("BUY");
        trade.setQuantity(BigDecimal.ONE);
        trade.setExecutionPrice(BigDecimal.ONE);

        assertThrows(ObjectInvalidException.class, () -> tradeService.createTrade(trade, authenticatedUserId));

        verify(tradeMapper, never()).createTrade(any(Trade.class));
    }

    // Verifies createTrade rejects missing instrument id.
    @Test
    void testCreateTradeThrowsWhenInstrumentIdIsNull() {
        Trade trade = new Trade();
        trade.setAccountId(UUID.randomUUID());
        trade.setInstrumentId(null);
        trade.setSide("BUY");
        trade.setQuantity(BigDecimal.ONE);
        trade.setExecutionPrice(BigDecimal.ONE);

        assertThrows(ObjectInvalidException.class, () -> tradeService.createTrade(trade, authenticatedUserId));

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
        trade.setQuantity(BigDecimal.ONE);
        trade.setExecutionPrice(BigDecimal.ONE);

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(null);

        assertThrows(ObjectNotFoundException.class, () -> tradeService.createTrade(trade, authenticatedUserId));

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
        trade.setQuantity(BigDecimal.ONE);
        trade.setExecutionPrice(BigDecimal.ONE);

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        assertThrows(ObjectInvalidException.class, () -> tradeService.createTrade(trade, authenticatedUserId));

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
        trade.setQuantity(BigDecimal.ZERO);
        trade.setExecutionPrice(BigDecimal.ONE);

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);

        assertThrows(ObjectInvalidException.class, () -> tradeService.createTrade(trade, authenticatedUserId));

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
        trade.setQuantity(BigDecimal.ONE);
        trade.setExecutionPrice(null);

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);
        instrument.setCurrentPrice(null);
        instrument.setUpdatedAt(OffsetDateTime.now());

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        doReturn(instrument).when(instrumentService).refreshPriceIfStale(instrumentId);

        assertThrows(ObjectInvalidException.class, () -> tradeService.createTrade(trade, authenticatedUserId));

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
        trade.setQuantity(BigDecimal.valueOf(2));
        trade.setExecutionPrice(BigDecimal.valueOf(10));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        when(tradeMapper.createTrade(trade)).thenReturn(0);

        assertThrows(ObjectNotProcessedException.class, () -> tradeService.createTrade(trade, authenticatedUserId));

        verify(tradeMapper, times(1)).createTrade(trade);
    }

    // Verifies createTrade keeps an existing executedAt value.
    @Test
    void testCreateTradeKeepsExistingExecutedAt() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        OffsetDateTime executedAt = OffsetDateTime.now().minusHours(3);

        Trade trade = new Trade();
        trade.setTradeId(UUID.randomUUID());
        trade.setAccountId(accountId);
        trade.setInstrumentId(instrumentId);
        trade.setSide("BUY");
        trade.setQuantity(BigDecimal.valueOf(2));
        trade.setExecutionPrice(BigDecimal.valueOf(15));
        trade.setExecutedAt(executedAt);

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        when(tradeMapper.createTrade(trade)).thenReturn(1);

        Trade result = tradeService.createTrade(trade, authenticatedUserId);

        assertNotNull(result);
        assertEquals(executedAt, result.getExecutedAt());
        verify(tradeMapper, times(1)).createTrade(trade);
    }

    // Verifies createTrade keeps an existing tradeValue value.
    @Test
    void testCreateTradeKeepsExistingTradeValue() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        BigDecimal presetTradeValue = BigDecimal.valueOf(777.77);

        Trade trade = new Trade();
        trade.setTradeId(UUID.randomUUID());
        trade.setAccountId(accountId);
        trade.setInstrumentId(instrumentId);
        trade.setSide("BUY");
        trade.setQuantity(BigDecimal.valueOf(10));
        trade.setExecutionPrice(BigDecimal.valueOf(99));
        trade.setTradeValue(presetTradeValue);

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        when(tradeMapper.createTrade(trade)).thenReturn(1);

        Trade result = tradeService.createTrade(trade, authenticatedUserId);

        assertNotNull(result);
        assertEquals(BigDecimal.valueOf(1000).setScale(4), result.getTradeValue());
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
        trade.setQuantity(BigDecimal.valueOf(5));
        trade.setExecutionPrice(BigDecimal.valueOf(100));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        Account account = new Account();
        account.setAccountId(accountId);
        account.setBalance(BigDecimal.valueOf(1000));

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        when(tradeMapper.createTrade(trade)).thenReturn(1);
        when(accountService.getAccountForTrade(accountId)).thenReturn(account);
        when(holdingService.findHolding(accountId, instrumentId)).thenReturn(null);

        Trade result = tradeService.createTrade(trade, authenticatedUserId);

        assertNotNull(result.getExecutedAt());
        assertEquals(BigDecimal.valueOf(500).setScale(4), result.getTradeValue());
        verify(tradeMapper, times(1)).createTrade(trade);
        verify(holdingService, times(1)).createHoldingInternal(any(Holding.class));
        verify(holdingService, never()).incrementHoldingQuantityInternal(
            any(UUID.class), any(UUID.class), any(BigDecimal.class));
        verify(accountService, times(1)).decrementBalance(
                accountId, BigDecimal.valueOf(500).setScale(4));
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
        trade.setQuantity(BigDecimal.valueOf(3));
        trade.setExecutionPrice(BigDecimal.valueOf(20));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        Account account = new Account();
        account.setAccountId(accountId);
        account.setBalance(BigDecimal.valueOf(500));

        Holding existingHolding = new Holding(accountId, instrumentId, BigDecimal.valueOf(10));

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        when(tradeMapper.createTrade(trade)).thenReturn(1);
        when(accountService.getAccount(accountId)).thenReturn(account);
        when(holdingService.findHolding(accountId, instrumentId)).thenReturn(existingHolding);

        Trade result = tradeService.createTrade(trade, authenticatedUserId);

        assertEquals(BigDecimal.valueOf(300).setScale(4), result.getTradeValue());
        verify(holdingService, never()).createHoldingInternal(any(Holding.class));
        verify(holdingService, times(1)).incrementHoldingQuantityInternal(
            accountId, instrumentId, BigDecimal.valueOf(3));
        verify(accountService, times(1)).decrementBalance(
                accountId, BigDecimal.valueOf(300).setScale(4));
    }

    // Verifies BUY trades increase quantity when holding already exists.
    @Test
    void testCreateTradeBuyExistingHoldingIncreasesQuantity() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        Trade trade = new Trade();
        trade.setTradeId(UUID.randomUUID());
        trade.setAccountId(accountId);
        trade.setInstrumentId(instrumentId);
        trade.setSide("BUY");
        trade.setQuantity(BigDecimal.valueOf(4));
        trade.setExecutionPrice(BigDecimal.valueOf(25));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        Account account = new Account();
        account.setAccountId(accountId);
        account.setBalance(BigDecimal.valueOf(1000));

        Holding existingHolding = new Holding(accountId, instrumentId, BigDecimal.valueOf(6));

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        when(tradeMapper.createTrade(trade)).thenReturn(1);
        when(accountService.getAccount(accountId)).thenReturn(account);
        when(holdingService.findHolding(accountId, instrumentId)).thenReturn(existingHolding);
        when(holdingService.incrementHoldingQuantityInternal(
            accountId, instrumentId, BigDecimal.valueOf(4))).thenAnswer(invocation -> {
            existingHolding.setQuantity(existingHolding.getQuantity().add(BigDecimal.valueOf(4)));
            return 1;
        });

        tradeService.createTrade(trade, authenticatedUserId);

        assertEquals(BigDecimal.valueOf(10), existingHolding.getQuantity());
        verify(holdingService, never()).createHoldingInternal(any(Holding.class));
        verify(holdingService, times(1)).incrementHoldingQuantityInternal(
            accountId, instrumentId, BigDecimal.valueOf(4));
        verify(accountService, times(1)).decrementBalance(
                accountId, BigDecimal.valueOf(400).setScale(4));
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
        trade.setQuantity(BigDecimal.valueOf(20));
        trade.setExecutionPrice(BigDecimal.valueOf(100));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        Account account = new Account();
        account.setAccountId(accountId);
        account.setBalance(BigDecimal.valueOf(500));

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        when(tradeMapper.createTrade(trade)).thenReturn(1);
        when(accountService.getAccountForTrade(accountId)).thenReturn(account);
        when(holdingService.findHolding(accountId, instrumentId)).thenReturn(null);
        doThrow(new RuntimeException("Insufficient balance")).when(accountService)
            .decrementBalance(accountId, BigDecimal.valueOf(2000));

        assertThrows(ObjectInvalidException.class, () -> tradeService.createTrade(trade, authenticatedUserId));

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
        trade.setQuantity(BigDecimal.valueOf(4));
        trade.setExecutionPrice(BigDecimal.valueOf(50));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        Holding existingHolding = new Holding(accountId, instrumentId, BigDecimal.valueOf(10));

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        when(tradeMapper.createTrade(trade)).thenReturn(1);
        when(holdingService.findHolding(accountId, instrumentId)).thenReturn(existingHolding);

        Trade result = tradeService.createTrade(trade, authenticatedUserId);

        assertEquals(BigDecimal.valueOf(400).setScale(4), result.getTradeValue());
        verify(holdingService, times(1)).decrementHoldingQuantityInternal(
            accountId, instrumentId, BigDecimal.valueOf(4));
        verify(accountService, times(1)).incrementBalance(
                accountId, BigDecimal.valueOf(400).setScale(4));
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
        trade.setQuantity(BigDecimal.valueOf(2));
        trade.setExecutionPrice(BigDecimal.valueOf(80));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        when(tradeMapper.createTrade(trade)).thenReturn(1);
        when(holdingService.findHolding(accountId, instrumentId)).thenReturn(null);

        assertThrows(ObjectInvalidException.class, () -> tradeService.createTrade(trade, authenticatedUserId));

        verify(holdingService, never()).decrementHoldingQuantityInternal(
            any(UUID.class), any(UUID.class), any(BigDecimal.class));
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
        trade.setQuantity(BigDecimal.valueOf(20));
        trade.setExecutionPrice(BigDecimal.valueOf(80));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        Holding existingHolding = new Holding(accountId, instrumentId, BigDecimal.valueOf(5));

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        when(tradeMapper.createTrade(trade)).thenReturn(1);
        when(holdingService.findHolding(accountId, instrumentId)).thenReturn(existingHolding);

        assertThrows(ObjectInvalidException.class, () -> tradeService.createTrade(trade, authenticatedUserId));

        verify(holdingService, never()).decrementHoldingQuantityInternal(
            any(UUID.class), any(UUID.class), any(BigDecimal.class));
        verify(accountService, never()).incrementBalance(any(UUID.class), any(BigDecimal.class));
    }

    // Verifies createTrade rejects unsupported side values.
    @Test
    void testCreateTradeReturnsTradeForUnsupportedSide() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        Trade trade = new Trade();
        trade.setTradeId(UUID.randomUUID());
        trade.setAccountId(accountId);
        trade.setInstrumentId(instrumentId);
        trade.setSide("HOLD");
        trade.setQuantity(BigDecimal.ONE);
        trade.setExecutionPrice(BigDecimal.valueOf(10));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);

        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
        when(tradeMapper.createTrade(trade)).thenReturn(1);

        assertThrows(ObjectInvalidException.class, () -> tradeService.createTrade(trade, authenticatedUserId));

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

        when(tradeMapper.findAllTradesForAccountWithInstrument(accountId, 50, 0)).thenReturn(expected);

        List<TradeResponse> result = tradeService.getAllTradesByAccountWithInstrument(accountId, null, null);

        assertEquals(expected, result);
        verify(tradeMapper, times(1)).findAllTradesForAccountWithInstrument(accountId, 50, 0);
    }

    // Verifies getAllTradesByAccountWithInstrument rejects null account ids.
    @Test
    void testGetAllTradesByAccountWithInstrumentThrowsWhenAccountIdNull() {
        assertThrows(ObjectNotFoundException.class,
                () -> tradeService.getAllTradesByAccountWithInstrument(null, null, null));

        verify(tradeMapper, never()).findAllTradesForAccountWithInstrument(any(UUID.class), any(Integer.class), any(Integer.class));
    }
}
