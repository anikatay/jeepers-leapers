package com.neueda.leap.service;

import com.neueda.leap.dto.response.MarketQuote;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.model.Instrument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

@Service
public class InstrumentPriceSyncService implements IService {

    private static final Logger log = LoggerFactory.getLogger(InstrumentPriceSyncService.class);

    private final MarketDataService marketDataService;
    private final InstrumentService instrumentService;
    private final long maxQuoteAgeSeconds;

    public InstrumentPriceSyncService(
            MarketDataService marketDataService,
            InstrumentService instrumentService,
            @Value("${trade.max-price-age-seconds:180}") long maxQuoteAgeSeconds) {
        this.marketDataService = marketDataService;
        this.instrumentService = instrumentService;
        this.maxQuoteAgeSeconds = maxQuoteAgeSeconds;
    }

    @Scheduled(fixedDelayString = "${instrument.price-sync-delay-ms:30000}")
    public void syncSubscribedTickerPricesToDb() {
        Map<String, Long> subscriptions = marketDataService.getSubscriptions();
        if (subscriptions.isEmpty()) {
            return;
        }

        for (String ticker : subscriptions.keySet()) {
            try {
                Optional<MarketQuote> latestQuote = marketDataService.getLatestQuote(ticker);
                if (latestQuote.isEmpty() || !isFresh(latestQuote.get().quoteTime())) {
                    continue;
                }

                MarketQuote quote = latestQuote.get();
                Instrument instrument = instrumentService.getInstrumentByTicker(ticker);
                var quoteTime = quote.quoteTime().atOffset(ZoneOffset.UTC);
                if (instrument.getUpdatedAt() == null || instrument.getUpdatedAt().isBefore(quoteTime)) {
                    instrumentService.updatePrice(
                            instrument.getInstrumentId(),
                            quote.price(),
                            quoteTime);
                }
            } catch (ObjectNotFoundException e) {
                log.debug("Skipping subscribed ticker not present in instruments table: {}", ticker);
            } catch (RuntimeException e) {
                log.warn("Unexpected error syncing ticker {}", ticker, e);
            }
        }
    }

    private boolean isFresh(Instant quoteTime) {
        Duration age = Duration.between(quoteTime, Instant.now());
        return !age.isNegative() && age.compareTo(Duration.ofSeconds(maxQuoteAgeSeconds)) <= 0;
    }
}
