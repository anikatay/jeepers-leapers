package com.neueda.leap.controller;

import com.neueda.leap.consumer.KafkaEventStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/kafka")
public class KafkaEventsController {
    
    private final KafkaEventStore eventStore;
    
    public KafkaEventsController(KafkaEventStore eventStore) {
        this.eventStore = eventStore;
    }
    
    @GetMapping("/events")
    public List<KafkaEventStore.KafkaEvent> getEvents() {
        return eventStore.getEvents();
    }
}