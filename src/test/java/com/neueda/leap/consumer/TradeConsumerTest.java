package com.neueda.leap.consumer;

import com.neueda.leap.event.TradeExecutedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class TradeConsumerTest {

    // Verifies a valid trade event is handled without throwing exceptions.
    @Test
    void testOnTradeExecutedHandlesValidEvent() {
        TradeConsumer consumer = new TradeConsumer();
        TradeExecutedEvent event = new TradeExecutedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SELL",
                BigDecimal.valueOf(5),
                BigDecimal.valueOf(250),
                BigDecimal.valueOf(1250),
                OffsetDateTime.now()
        );

        assertDoesNotThrow(() -> consumer.onTradeExecuted(event));
    }
}
