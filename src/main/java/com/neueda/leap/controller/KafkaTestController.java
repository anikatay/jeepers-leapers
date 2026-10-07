package com.neueda.leap.controller;

import com.neueda.leap.event.TradeExecutedEvent;
import com.neueda.leap.producer.TradeEventProducer;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/kafka-test")
public class KafkaTestController {

    private final TradeEventProducer producer;

    public KafkaTestController(TradeEventProducer producer) {
        this.producer = producer;
    }

    @PostMapping
    public String send() {
        producer.publish(new TradeExecutedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "BUY", 10, new BigDecimal("229.0000"), new BigDecimal("2290.0000"),
                OffsetDateTime.now()));
        return "sent";
    }
}