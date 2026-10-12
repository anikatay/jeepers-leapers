package com.neueda.leap.consumer;

import com.neueda.leap.event.TradeExecutedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaEventStoreTest {

    // Verifies a received trade event is stored in the event list.
    @Test
    void testListenStoresEvent() {
        KafkaEventStore store = new KafkaEventStore();

        TradeExecutedEvent event = eventWithTradeId(UUID.randomUUID());
        store.listen(event);

        List<KafkaEventStore.KafkaEvent> events = store.getEvents();
        assertEquals(1, events.size());
        assertNotNull(events.get(0).timestamp);
        assertTrue(events.get(0).message.contains(event.tradeId().toString()));
    }

    // Verifies the store limits retained events to a maximum of 100.
    @Test
    void testListenKeepsAtMostHundredEvents() {
        KafkaEventStore store = new KafkaEventStore();

        for (int i = 0; i < 120; i++) {
            store.listen(eventWithTradeId(UUID.randomUUID()));
        }

        List<KafkaEventStore.KafkaEvent> events = store.getEvents();
        assertEquals(100, events.size());
    }

    // Verifies the oldest events are removed once the store is full.
    @Test
    void testListenRemovesOldestEventsWhenFull() {
        KafkaEventStore store = new KafkaEventStore();

        UUID oldestTradeId = UUID.randomUUID();
        store.listen(eventWithTradeId(oldestTradeId));

        for (int i = 0; i < 100; i++) {
            store.listen(eventWithTradeId(UUID.randomUUID()));
        }

        List<KafkaEventStore.KafkaEvent> events = store.getEvents();
        assertEquals(100, events.size());
        assertTrue(events.stream().noneMatch(e -> e.message.contains(oldestTradeId.toString())));
    }

    // Verifies getEvents returns a defensive copy, not the internal backing list.
    @Test
    void testGetEventsReturnsDefensiveCopy() {
        KafkaEventStore store = new KafkaEventStore();

        store.listen(eventWithTradeId(UUID.randomUUID()));

        List<KafkaEventStore.KafkaEvent> snapshot = store.getEvents();
        int originalSize = snapshot.size();

        snapshot.clear();

        List<KafkaEventStore.KafkaEvent> afterMutation = store.getEvents();
        assertEquals(originalSize, afterMutation.size());
    }

    private TradeExecutedEvent eventWithTradeId(UUID tradeId) {
        return new TradeExecutedEvent(
                tradeId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "BUY",
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(200),
                OffsetDateTime.now()
        );
    }
}
