package com.neueda.leap.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.neueda.leap.event.TradeExecutedEvent;

@Component
public class TradeEventProducer {
    private static final Logger logger = LoggerFactory.getLogger(TradeEventProducer.class);
    private static final String TOPIC = "trade-executed";

    private final KafkaTemplate<String, TradeExecutedEvent> kafkaTemplate;

    public TradeEventProducer(KafkaTemplate<String, TradeExecutedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(TradeExecutedEvent event) {
        kafkaTemplate.send(TOPIC, event.tradeId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        logger.error("Failed to publish trade event {}", event.tradeId(), ex);
                    } else {
                        logger.info("Published trade event {} to partition {} offset {}",
                                event.tradeId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
