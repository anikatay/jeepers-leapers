package com.neueda.leap.analysis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/**
 * Caps analysis requests per calendar minute, overall and per client, so the endpoint cannot run
 * up OpenAI cost. In memory, so the limits apply per app instance.
 */
@Component
public class AnalysisRateLimiter {

    private static final long MINUTE_MS = 60_000;

    private final int globalPerMinute;
    private final int perClientPerMinute;
    private final Clock clock;

    private long currentMinute = -1;
    private int globalCount;
    private final Map<String, Integer> clientCounts = new HashMap<>();

    @Autowired
    public AnalysisRateLimiter(
            @Value("${analysis.rate-limit.global-per-minute}") int globalPerMinute,
            @Value("${analysis.rate-limit.per-client-per-minute}") int perClientPerMinute) {
        this(globalPerMinute, perClientPerMinute, Clock.systemUTC());
    }

    public AnalysisRateLimiter(int globalPerMinute, int perClientPerMinute, Clock clock) {
        this.globalPerMinute = globalPerMinute;
        this.perClientPerMinute = perClientPerMinute;
        this.clock = clock;
    }

    /** Counts one request from the client, or throws if it would exceed either limit. */
    public synchronized void acquire(String client) {
        long now = clock.millis();
        long minute = now / MINUTE_MS;
        if (minute != currentMinute) {
            currentMinute = minute;
            globalCount = 0;
            clientCounts.clear();
        }

        int clientCount = clientCounts.getOrDefault(client, 0);
        if (globalCount >= globalPerMinute || clientCount >= perClientPerMinute) {
            long secondsToNextMinute = ((minute + 1) * MINUTE_MS - now + 999) / 1000;
            throw new TooManyAnalysesException(
                "Too many trade analyses; try again in " + secondsToNextMinute + " seconds",
                secondsToNextMinute);
        }
        globalCount++;
        clientCounts.put(client, clientCount + 1);
    }
}
