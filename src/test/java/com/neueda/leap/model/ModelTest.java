package com.neueda.leap.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelTest {

    // Verifies User default status and accessor behavior.
    @Test
    void testUserDefaultsAndAccessors() {
        User user = new User();
        UUID userId = UUID.randomUUID();

        assertEquals("ACTIVE", user.getStatus());

        user.setUserId(userId);
        user.setRole("ROLE_CUSTOMER");
        user.setStatus("INACTIVE");

        assertEquals(userId, user.getUserId());
        assertEquals("ROLE_CUSTOMER", user.getRole());
        assertEquals("INACTIVE", user.getStatus());
    }

    // Verifies UserPii accessors for all PII fields.
    @Test
    void testUserPiiAccessors() {
        UserPii userPii = new UserPii();
        UUID userId = UUID.randomUUID();
        OffsetDateTime dob = OffsetDateTime.parse("1990-05-20T00:00:00Z");

        userPii.setUserId(userId);
        userPii.setFirstName("Alex");
        userPii.setLastName("Smith");
        userPii.setDateOfBirth(dob);
        userPii.setSsn("111-22-3333");
        userPii.setAddress("123 Main St");
        userPii.setPhoneNumber("555-0100");
        userPii.setEmail("alex@example.com");

        assertEquals(userId, userPii.getUserId());
        assertEquals("Alex", userPii.getFirstName());
        assertEquals("Smith", userPii.getLastName());
        assertEquals(dob, userPii.getDateOfBirth());
        assertEquals("111-22-3333", userPii.getSsn());
        assertEquals("123 Main St", userPii.getAddress());
        assertEquals("555-0100", userPii.getPhoneNumber());
        assertEquals("alex@example.com", userPii.getEmail());
    }

    // Verifies Holding constructor, quantity mutation, and equality semantics.
    @Test
    void testHoldingBehavior() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        Holding holding = new Holding(accountId, instrumentId, BigDecimal.valueOf(10));

        assertEquals(accountId, holding.getAccountId());
        assertEquals(instrumentId, holding.getInstrumentId());
        assertEquals(BigDecimal.valueOf(10), holding.getQuantity());

        holding.setQuantity(BigDecimal.valueOf(15));
        assertEquals(BigDecimal.valueOf(15), holding.getQuantity());

        Holding sameIdsDifferentQty = new Holding(accountId, instrumentId, BigDecimal.valueOf(99));
        Holding differentIds = new Holding(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE);

        assertTrue(holding.equals(holding));
        assertTrue(holding.equals(sameIdsDifferentQty));
        assertFalse(holding.equals(differentIds));
        assertFalse(holding.equals("not-a-holding"));
    }

    // Verifies Exchange defaults, full constructor values, and mutators.
    @Test
    void testExchangeDefaultsConstructorsAndSetters() {
        Exchange defaultExchange = new Exchange();
        assertEquals("USD", defaultExchange.getCurrency());

        Exchange exchange = new Exchange("XNYS", "NYSE", "US", "America/New_York", "USD");
        assertEquals("XNYS", exchange.getExchangeId());
        assertEquals("NYSE", exchange.getName());
        assertEquals("US", exchange.getRegion());
        assertEquals("America/New_York", exchange.getTimezone());
        assertEquals("USD", exchange.getCurrency());

        exchange.setExchangeId("XNAS");
        exchange.setName("NASDAQ");
        exchange.setRegion("NA");
        exchange.setTimezone("UTC");
        exchange.setCurrency("EUR");

        assertEquals("XNAS", exchange.getExchangeId());
        assertEquals("NASDAQ", exchange.getName());
        assertEquals("NA", exchange.getRegion());
        assertEquals("UTC", exchange.getTimezone());
        assertEquals("EUR", exchange.getCurrency());
    }

    // Verifies Account defaults and property accessors.
    @Test
    void testAccountDefaultsAndAccessors() {
        Account account = new Account();

        assertEquals("USD", account.getCurrency());
        assertEquals(BigDecimal.ZERO, account.getBalance());
        assertEquals("ACTIVE", account.getStatus());
        assertNull(account.getCreatedAt());
        assertNull(account.getUpdatedAt());

        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now().minusDays(1);
        OffsetDateTime updatedAt = OffsetDateTime.now();

        account.setAccountId(accountId);
        account.setUserId(userId);
        account.setCurrency("GBP");
        account.setBalance(BigDecimal.valueOf(123.45));
        account.setStatus("CLOSED");
        account.setCreatedAt(createdAt);
        account.setUpdatedAt(updatedAt);

        assertEquals(accountId, account.getAccountId());
        assertEquals(userId, account.getUserId());
        assertEquals("GBP", account.getCurrency());
        assertEquals(BigDecimal.valueOf(123.45), account.getBalance());
        assertEquals("CLOSED", account.getStatus());
        assertEquals(createdAt, account.getCreatedAt());
        assertEquals(updatedAt, account.getUpdatedAt());
    }
}
