package com.neueda.leap.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.dto.response.MarketQuote;
import com.neueda.leap.service.MarketDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MarketQuoteWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MarketQuoteWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final MarketDataService marketDataService;
    private final Map<String, Set<WebSocketSession>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sessionTickers = new ConcurrentHashMap<>();

    public MarketQuoteWebSocketHandler(ObjectMapper objectMapper, MarketDataService marketDataService) {
        this.objectMapper = objectMapper;
        this.marketDataService = marketDataService;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Set<String> tickers = sessionTickers.get(session.getId());
        if (tickers == null) {
            return;
        }
        Set<String> snapshot = Set.copyOf(tickers);
        snapshot.forEach(ticker -> removeSubscription(session, ticker));
        sessionTickers.remove(session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        JsonNode request = objectMapper.readTree(message.getPayload());
        String action = request.path("action").asText("").toUpperCase();
        String ticker = request.path("ticker").asText("");

        if (!"SUBSCRIBE".equals(action) && !"UNSUBSCRIBE".equals(action)) {
            sendError(session, "action must be SUBSCRIBE or UNSUBSCRIBE");
            return;
        }

        if ("SUBSCRIBE".equals(action)) {
            addSubscription(session, ticker);
        } else {
            removeSubscription(session, ticker.trim().toUpperCase());
        }
    }

    public void broadcast(MarketQuote quote) {
        Set<WebSocketSession> sessions = subscribers.get(quote.ticker());
        if (sessions == null) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(quote);
        } catch (IOException e) {
            log.warn("Could not serialize quote for {}", quote.ticker(), e);
            return;
        }

        sessions.removeIf(session -> {
            if (!session.isOpen()) {
                return true;
            }
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
                return false;
            } catch (IOException e) {
                log.warn("Could not send quote to websocket session {}", session.getId(), e);
                return true;
            }
        });
    }

    private void addSubscription(WebSocketSession session, String rawTicker) {
        String ticker;
        try {
            ticker = marketDataService.normalizeForSubscription(rawTicker);
        } catch (RuntimeException e) {
            sendError(session, e.getMessage());
            return;
        }

        Set<String> tickers = sessionTickers.computeIfAbsent(
                session.getId(), ignored -> ConcurrentHashMap.newKeySet());
        if (tickers.add(ticker)) {
            try {
                marketDataService.subscribeTicker(ticker);
            } catch (RuntimeException e) {
                tickers.remove(ticker);
                sendError(session, e.getMessage());
                return;
            }
            subscribers.computeIfAbsent(ticker, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        }
    }

    private void removeSubscription(WebSocketSession session, String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return;
        }
        Set<String> tickers = sessionTickers.get(session.getId());
        if (tickers == null || !tickers.remove(ticker)) {
            return;
        }
        Set<WebSocketSession> sessions = subscribers.get(ticker);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                subscribers.remove(ticker, sessions);
            }
        }
        marketDataService.unsubscribeTicker(ticker);
    }

    private void sendError(WebSocketSession session, String message) {
        try {
            session.sendMessage(new TextMessage(objectMapper.createObjectNode()
                    .put("type", "ERROR")
                    .put("message", message == null ? "Invalid request" : message)
                    .toString()));
        } catch (IOException e) {
            log.warn("Could not send websocket error to session {}", session.getId(), e);
        }
    }
}
