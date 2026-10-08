package com.neueda.leap.advice;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.neueda.leap.event.TradeExecutedEvent;
import com.neueda.leap.producer.TradeEventProducer;

@Component
public class TradeEventForwarder {
    private final TradeEventProducer producer;

    public TradeEventForwarder(TradeEventProducer producer){
        this.producer = producer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTradeCreated(TradeExecutedEvent event){
        producer.publish(event);
    }
}
