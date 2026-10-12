package com.neueda.leap.service;

import com.neueda.leap.dto.request.HoldingRequest;
import com.neueda.leap.dto.response.HoldingResponse;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.mapper.HoldingMapper;
import com.neueda.leap.model.Holding;
import com.neueda.leap.model.Instrument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldingServiceTest {

    @Mock
    private HoldingMapper holdingMapper;

    @Mock
    private InstrumentService instrumentService;

    @InjectMocks
    private HoldingService holdingService;

    // Verifies getHoldingsByAccountId returns mapped holding responses.
    @Test
    void testGetHoldingsByAccountIdReturnsMappedResponses() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        Holding holding = new Holding(accountId, instrumentId, BigDecimal.valueOf(20));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);
        instrument.setTicker("AAPL");

        when(holdingMapper.getHoldingsByAccountId(accountId)).thenReturn(List.of(holding));
        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);

        List<HoldingResponse> responses = holdingService.getHoldingsByAccountId(accountId);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(accountId, responses.get(0).accountId());
        assertEquals(instrumentId, responses.get(0).instrumentId());
        assertEquals("AAPL", responses.get(0).ticker());
        assertEquals(BigDecimal.valueOf(20), responses.get(0).quantity());
        verify(holdingMapper, times(1)).getHoldingsByAccountId(accountId);
        verify(instrumentService, times(1)).getInstrumentById(instrumentId);
    }

    // Verifies getHoldingsByAccountId returns empty list when no holdings exist.
    @Test
    void testGetHoldingsByAccountIdReturnsEmptyWhenNoRows() {
        UUID accountId = UUID.randomUUID();

        when(holdingMapper.getHoldingsByAccountId(accountId)).thenReturn(List.of());

        List<HoldingResponse> responses = holdingService.getHoldingsByAccountId(accountId);

        assertNotNull(responses);
        assertEquals(0, responses.size());

        verify(holdingMapper, times(1)).getHoldingsByAccountId(accountId);
    }

    // Verifies getHoldingByAccountIdAndInstrumentId returns a mapped response.
    @Test
    void testGetHoldingByAccountIdAndInstrumentIdReturnsResponse() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        Holding holding = new Holding(accountId, instrumentId, BigDecimal.valueOf(8));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);
        instrument.setTicker("MSFT");

        when(holdingMapper.getHoldingByAccountIdAndInstrumentId(accountId, instrumentId)).thenReturn(holding);
        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);

        HoldingResponse response = holdingService.getHoldingByAccountIdAndInstrumentId(accountId, instrumentId);

        assertNotNull(response);
        assertEquals(accountId, response.accountId());
        assertEquals(instrumentId, response.instrumentId());
        assertEquals("MSFT", response.ticker());
        assertEquals(BigDecimal.valueOf(8), response.quantity());
        verify(holdingMapper, times(1)).getHoldingByAccountIdAndInstrumentId(accountId, instrumentId);
        verify(instrumentService, times(1)).getInstrumentById(instrumentId);
    }

    // Verifies getHoldingByAccountIdAndInstrumentId throws when holding is missing.
    @Test
    void testGetHoldingByAccountIdAndInstrumentIdThrowsWhenMissing() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        when(holdingMapper.getHoldingByAccountIdAndInstrumentId(accountId, instrumentId)).thenReturn(null);

        assertThrows(ObjectNotFoundException.class,
                () -> holdingService.getHoldingByAccountIdAndInstrumentId(accountId, instrumentId));

        verify(holdingMapper, times(1)).getHoldingByAccountIdAndInstrumentId(accountId, instrumentId);
    }

    // Verifies findHolding returns mapper result.
    @Test
    void testFindHoldingReturnsMapperResult() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        Holding holding = new Holding(accountId, instrumentId, BigDecimal.valueOf(5));

        when(holdingMapper.getHoldingByAccountIdAndInstrumentId(accountId, instrumentId)).thenReturn(holding);

        Holding result = holdingService.findHolding(accountId, instrumentId);

        assertEquals(holding, result);
        verify(holdingMapper, times(1)).getHoldingByAccountIdAndInstrumentId(accountId, instrumentId);
    }

    // Verifies findHolding can return null when no row exists.
    @Test
    void testFindHoldingReturnsNullWhenMissing() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        when(holdingMapper.getHoldingByAccountIdAndInstrumentId(accountId, instrumentId)).thenReturn(null);

        Holding result = holdingService.findHolding(accountId, instrumentId);

        assertNull(result);
        verify(holdingMapper, times(1)).getHoldingByAccountIdAndInstrumentId(accountId, instrumentId);
    }

    // Verifies createHoldingInternal returns holding when insert succeeds.
    @Test
    void testCreateHoldingInternalReturnsHoldingWhenCreated() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        Holding holding = new Holding(accountId, instrumentId, BigDecimal.valueOf(11));

        when(holdingMapper.insertHolding(accountId, instrumentId, BigDecimal.valueOf(11))).thenReturn(1);

        Holding result = holdingService.createHoldingInternal(holding);

        assertEquals(holding, result);
        verify(holdingMapper, times(1)).insertHolding(accountId, instrumentId, BigDecimal.valueOf(11));
    }

    // Verifies createHoldingInternal throws when insert fails.
    @Test
    void testCreateHoldingInternalThrowsWhenInsertFails() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        Holding holding = new Holding(accountId, instrumentId, BigDecimal.valueOf(11));

        when(holdingMapper.insertHolding(accountId, instrumentId, BigDecimal.valueOf(11))).thenReturn(0);

        assertThrows(ObjectNotProcessedException.class, () -> holdingService.createHoldingInternal(holding));

        verify(holdingMapper, times(1)).insertHolding(accountId, instrumentId, BigDecimal.valueOf(11));
    }

    // Verifies createHolding returns a response when insert succeeds.
    @Test
    void testCreateHoldingReturnsResponseWhenCreated() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        HoldingRequest request = new HoldingRequest(accountId, instrumentId, BigDecimal.valueOf(14));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);
        instrument.setTicker("GOOG");

        when(holdingMapper.insertHolding(accountId, instrumentId, BigDecimal.valueOf(14))).thenReturn(1);
        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);

        HoldingResponse response = holdingService.createHolding(request);

        assertNotNull(response);
        assertEquals(accountId, response.accountId());
        assertEquals(instrumentId, response.instrumentId());
        assertEquals("GOOG", response.ticker());
        assertEquals(BigDecimal.valueOf(14), response.quantity());
        verify(holdingMapper, times(1)).insertHolding(accountId, instrumentId, BigDecimal.valueOf(14));
        verify(instrumentService, times(1)).getInstrumentById(instrumentId);
    }

    // Verifies createHolding throws when insert fails.
    @Test
    void testCreateHoldingThrowsWhenInsertFails() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        HoldingRequest request = new HoldingRequest(accountId, instrumentId, BigDecimal.valueOf(14));

        when(holdingMapper.insertHolding(accountId, instrumentId, BigDecimal.valueOf(14))).thenReturn(0);

        assertThrows(ObjectNotProcessedException.class, () -> holdingService.createHolding(request));

        verify(holdingMapper, times(1)).insertHolding(accountId, instrumentId, BigDecimal.valueOf(14));
    }

    // Verifies incrementHoldingQuantityImternal updates quantity when valid.
    @Test
    void testIncrementHoldingQuantityImternalReturnsOneWhenSuccessful() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        Holding holding = new Holding(accountId, instrumentId, BigDecimal.valueOf(10));

        when(holdingMapper.updateHoldingQuantity(accountId, instrumentId, BigDecimal.valueOf(15))).thenReturn(1);

        int result = holdingService.incrementHoldingQuantityImternal(holding, BigDecimal.valueOf(5));

        assertEquals(1, result);
        verify(holdingMapper, times(1)).updateHoldingQuantity(accountId, instrumentId, BigDecimal.valueOf(15));
    }

    // Verifies incrementHoldingQuantityImternal rejects null holding.
    @Test
    void testIncrementHoldingQuantityImternalThrowsForNullHolding() {
        assertThrows(ObjectInvalidException.class, () -> holdingService.incrementHoldingQuantityImternal(null, BigDecimal.valueOf(5)));
    }

    // Verifies incrementHoldingQuantityImternal rejects nonpositive amount.
    @Test
    void testIncrementHoldingQuantityImternalThrowsForInvalidAmount() {
        Holding holding = new Holding(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(10));

        assertThrows(ObjectInvalidException.class, () -> holdingService.incrementHoldingQuantityImternal(holding, BigDecimal.ZERO));
    }

    // Verifies incrementHoldingQuantityImternal throws when update fails.
    @Test
    void testIncrementHoldingQuantityImternalThrowsWhenUpdateFails() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        Holding holding = new Holding(accountId, instrumentId, BigDecimal.valueOf(10));

        when(holdingMapper.updateHoldingQuantity(accountId, instrumentId, BigDecimal.valueOf(15))).thenReturn(0);

        assertThrows(ObjectNotProcessedException.class,
                () -> holdingService.incrementHoldingQuantityImternal(holding, BigDecimal.valueOf(5)));

            verify(holdingMapper, times(1)).updateHoldingQuantity(accountId, instrumentId, BigDecimal.valueOf(15));
    }

    // Verifies decrementHoldingQuantityImternal updates quantity when remainder is positive.
    @Test
    void testDecrementHoldingQuantityImternalUpdatesWhenRemainderPositive() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        Holding holding = new Holding(accountId, instrumentId, BigDecimal.valueOf(10));

        when(holdingMapper.updateHoldingQuantity(accountId, instrumentId, BigDecimal.valueOf(7))).thenReturn(1);

        int result = holdingService.decrementHoldingQuantityImternal(holding, BigDecimal.valueOf(3));

        assertEquals(1, result);
        verify(holdingMapper, times(1)).updateHoldingQuantity(accountId, instrumentId, BigDecimal.valueOf(7));
    }

    // Verifies decrementHoldingQuantityImternal deletes row when new quantity is zero.
    @Test
    void testDecrementHoldingQuantityImternalDeletesWhenQuantityBecomesZero() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        Holding holding = new Holding(accountId, instrumentId, BigDecimal.valueOf(10));

        when(holdingMapper.deleteHolding(accountId, instrumentId)).thenReturn(1);

        int result = holdingService.decrementHoldingQuantityImternal(holding, BigDecimal.valueOf(10));

        assertEquals(1, result);
        verify(holdingMapper, times(1)).deleteHolding(accountId, instrumentId);
    }

    // Verifies decrementHoldingQuantityImternal rejects null holding.
    @Test
    void testDecrementHoldingQuantityImternalThrowsForNullHolding() {
        assertThrows(ObjectInvalidException.class, () -> holdingService.decrementHoldingQuantityImternal(null, BigDecimal.ONE));
    }

    // Verifies decrementHoldingQuantityImternal rejects nonpositive amount.
    @Test
    void testDecrementHoldingQuantityImternalThrowsForInvalidAmount() {
        Holding holding = new Holding(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(10));

        assertThrows(ObjectInvalidException.class, () -> holdingService.decrementHoldingQuantityImternal(holding, BigDecimal.ZERO));
    }

    // Verifies decrementHoldingQuantityImternal rejects amount greater than quantity.
    @Test
    void testDecrementHoldingQuantityImternalThrowsWhenAmountExceedsQuantity() {
        Holding holding = new Holding(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(10));

        assertThrows(ObjectInvalidException.class, () -> holdingService.decrementHoldingQuantityImternal(holding, BigDecimal.valueOf(11)));
    }

    // Verifies decrementHoldingQuantityImternal throws when mapper change fails.
    @Test
    void testDecrementHoldingQuantityImternalThrowsWhenMapperFails() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        Holding holding = new Holding(accountId, instrumentId, BigDecimal.valueOf(10));

        when(holdingMapper.updateHoldingQuantity(accountId, instrumentId, BigDecimal.valueOf(7))).thenReturn(0);

        assertThrows(ObjectNotProcessedException.class,
                () -> holdingService.decrementHoldingQuantityImternal(holding, BigDecimal.valueOf(3)));

            verify(holdingMapper, times(1)).updateHoldingQuantity(accountId, instrumentId, BigDecimal.valueOf(7));
    }

    // Verifies updateHoldingQuantity returns updated response when mapper succeeds.
    @Test
    void testUpdateHoldingQuantityReturnsResponseWhenUpdated() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        HoldingRequest request = new HoldingRequest(accountId, instrumentId, BigDecimal.valueOf(50));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(instrumentId);
        instrument.setTicker("TSLA");

        when(holdingMapper.updateHoldingQuantity(accountId, instrumentId, BigDecimal.valueOf(50))).thenReturn(1);
        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);

        HoldingResponse response = holdingService.updateHoldingQuantity(request);

        assertNotNull(response);
        assertEquals(accountId, response.accountId());
        assertEquals(instrumentId, response.instrumentId());
        assertEquals("TSLA", response.ticker());
        assertEquals(BigDecimal.valueOf(50), response.quantity());
        verify(holdingMapper, times(1)).updateHoldingQuantity(accountId, instrumentId, BigDecimal.valueOf(50));
        verify(instrumentService, times(1)).getInstrumentById(instrumentId);
    }

    // Verifies updateHoldingQuantity throws when mapper update fails.
    @Test
    void testUpdateHoldingQuantityThrowsWhenUpdateFails() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        HoldingRequest request = new HoldingRequest(accountId, instrumentId, BigDecimal.valueOf(50));

        when(holdingMapper.updateHoldingQuantity(accountId, instrumentId, BigDecimal.valueOf(50))).thenReturn(0);

        assertThrows(ObjectNotProcessedException.class, () -> holdingService.updateHoldingQuantity(request));

        verify(holdingMapper, times(1)).updateHoldingQuantity(accountId, instrumentId, BigDecimal.valueOf(50));
    }

    // Verifies deleteHolding delegates to mapper.
    @Test
    void testDeleteHoldingDelegatesToMapper() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        assertDoesNotThrow(() -> holdingService.deleteHolding(accountId, instrumentId));

        verify(holdingMapper, times(1)).deleteHolding(accountId, instrumentId);
    }

    // Verifies deleteHoldingsByAccountId delegates to mapper.
    @Test
    void testDeleteHoldingsByAccountIdDelegatesToMapper() {
        UUID accountId = UUID.randomUUID();

        assertDoesNotThrow(() -> holdingService.deleteHoldingsByAccountId(accountId));

        verify(holdingMapper, times(1)).deleteHoldingsByAccountId(accountId);
    }
}
