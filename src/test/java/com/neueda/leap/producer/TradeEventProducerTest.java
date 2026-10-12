package com.neueda.leap.producer;

import com.neueda.leap.event.TradeExecutedEvent;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeEventProducerTest {

    @Mock
    private KafkaTemplate<String, TradeExecutedEvent> kafkaTemplate;

    // Verifies publish sends to the trade-executed topic with tradeId as key.
    @Test
    void testPublishSendsToExpectedTopicAndKey() {
        TradeExecutedEvent event = event();
        CompletableFuture<SendResult<String, TradeExecutedEvent>> future = new CompletableFuture<>();
        when(kafkaTemplate.send("trade-executed", event.tradeId().toString(), event)).thenReturn(future);

        TradeEventProducer producer = new TradeEventProducer(kafkaTemplate);
        producer.publish(event);

        verify(kafkaTemplate).send("trade-executed", event.tradeId().toString(), event);
    }

    // Verifies successful send callback completes without throwing.
    @Test
    void testPublishHandlesSuccessfulSendCallback() {
        TradeExecutedEvent event = event();
        CompletableFuture<SendResult<String, TradeExecutedEvent>> future = new CompletableFuture<>();

        @SuppressWarnings("unchecked")
        SendResult<String, TradeExecutedEvent> sendResult = org.mockito.Mockito.mock(SendResult.class);
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("trade-executed", 1),
                0,
                42,
                System.currentTimeMillis(),
                Long.valueOf(0),
                0,
                0
        );

        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(kafkaTemplate.send("trade-executed", event.tradeId().toString(), event)).thenReturn(future);

        TradeEventProducer producer = new TradeEventProducer(kafkaTemplate);

        assertDoesNotThrow(() -> {
            producer.publish(event);
            future.complete(sendResult);
        });

        verify(kafkaTemplate).send("trade-executed", event.tradeId().toString(), event);
    }

    // Verifies failed send callback completes without throwing.
    @Test
    void testPublishHandlesFailedSendCallback() {
        TradeExecutedEvent event = event();
        CompletableFuture<SendResult<String, TradeExecutedEvent>> future = new CompletableFuture<>();
        when(kafkaTemplate.send("trade-executed", event.tradeId().toString(), event)).thenReturn(future);

        TradeEventProducer producer = new TradeEventProducer(kafkaTemplate);

        assertDoesNotThrow(() -> {
            producer.publish(event);
            future.completeExceptionally(new RuntimeException("kafka down"));
        });

        verify(kafkaTemplate).send("trade-executed", event.tradeId().toString(), event);
    }

    private TradeExecutedEvent event() {
        return new TradeExecutedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "BUY",
                BigDecimal.valueOf(5),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(500),
                OffsetDateTime.now()
        );
    }
}
