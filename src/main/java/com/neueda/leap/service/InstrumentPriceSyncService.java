package com.neueda.leap.service;

import com.neueda.leap.dto.response.HistoryResponse;
import com.neueda.leap.dto.response.PriceBar;
import com.neueda.leap.exception.MarketDataUnavailableException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.model.Instrument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class InstrumentPriceSyncService implements IService {

    private static final Logger log = LoggerFactory.getLogger(InstrumentPriceSyncService.class);

    private final MarketDataService marketDataService;
    private final InstrumentService instrumentService;

    public InstrumentPriceSyncService(MarketDataService marketDataService, InstrumentService instrumentService) {
        this.marketDataService = marketDataService;
        this.instrumentService = instrumentService;
    }

    @Scheduled(fixedDelayString = "${instrument.price-sync-delay-ms:30000}")
    public void syncSubscribedTickerPricesToDb() {
        Map<String, Long> subscriptions = marketDataService.getSubscriptions();
        if (subscriptions.isEmpty()) {
            return;
        }

        for (String ticker : subscriptions.keySet()) {
            try {
                HistoryResponse history = marketDataService.getHistory(ticker, "1mo");
                if (history.bars() == null || history.bars().isEmpty()) {
                    continue;
                }

                PriceBar latestBar = history.bars().get(history.bars().size() - 1);
                BigDecimal latestClose = latestBar.close();
                if (latestClose == null) {
                    continue;
                }

                Instrument instrument = instrumentService.getInstrumentByTicker(ticker);
                if (instrument.getCurrentPrice() == null || instrument.getCurrentPrice().compareTo(latestClose) != 0) {
                    instrumentService.updatePrice(instrument.getInstrumentId(), latestClose);
                }
            } catch (ObjectNotFoundException e) {
                log.debug("Skipping subscribed ticker not present in instruments table: {}", ticker);
            } catch (MarketDataUnavailableException e) {
                log.warn("Market data unavailable during sync for ticker {}: {}", ticker, e.getMessage());
            } catch (RuntimeException e) {
                log.warn("Unexpected error syncing ticker {}", ticker, e);
            }
        }
    }
}
