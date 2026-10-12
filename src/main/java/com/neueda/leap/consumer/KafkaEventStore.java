package com.neueda.leap.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.neueda.leap.event.TradeExecutedEvent;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class KafkaEventStore {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaEventStore.class);
    private final List<KafkaEvent> events = new CopyOnWriteArrayList<>();
    private static final int MAX_EVENTS = 100; // Keep last 100 events
    
    @KafkaListener(topics = "trade-executed")
    public void listen(TradeExecutedEvent event) {
        logger.info("Received event: {}", event);
        
        KafkaEvent kafkaEvent = new KafkaEvent(System.currentTimeMillis(), event.toString());
        events.add(kafkaEvent);
        
        // Keep only recent events
        if (events.size() > MAX_EVENTS) {
            events.remove(0);
        }
    }
    
    public List<KafkaEvent> getEvents() {
        return new LinkedList<>(events);
    }
    
    public static class KafkaEvent {
        public long timestamp;
        public String message;
        
        public KafkaEvent(long timestamp, String message) {
            this.timestamp = timestamp;
            this.message = message;
        }
    }
}