package com.neueda.leap.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.neueda.leap.event.TradeExecutedEvent;




@Component 
public class TradeConsumer {
	private static final Logger logger = LoggerFactory.getLogger(TradeConsumer.class);
	@KafkaListener(topics = "trade-executed")
	public void onTradeExecuted(TradeExecutedEvent event){
		logger.info("Consumed trade event {} ({} {} @ {})", 
						event.tradeId(), event.side(), event.quantity(), event.executionPrice());
	}

}
