package com.neueda.leap.analysis;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalysisRateLimiterTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-15T10:00:15Z"));
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };

    @Test
    void capsEachClientPerMinute() {
        AnalysisRateLimiter limiter = new AnalysisRateLimiter(100, 2, clock);
        limiter.acquire("a");
        limiter.acquire("a");

        TooManyAnalysesException e = assertThrows(TooManyAnalysesException.class, () -> limiter.acquire("a"));
        assertEquals(45, e.getRetryAfterSeconds());
        assertDoesNotThrow(() -> limiter.acquire("b"));
    }

    @Test
    void capsAllClientsTogether() {
        AnalysisRateLimiter limiter = new AnalysisRateLimiter(2, 100, clock);
        limiter.acquire("a");
        limiter.acquire("b");

        assertThrows(TooManyAnalysesException.class, () -> limiter.acquire("c"));
    }

    @Test
    void startsAgainInTheNextMinute() {
        AnalysisRateLimiter limiter = new AnalysisRateLimiter(1, 1, clock);
        limiter.acquire("a");
        assertThrows(TooManyAnalysesException.class, () -> limiter.acquire("a"));

        now.set(Instant.parse("2026-10-15T10:01:00Z"));

        assertDoesNotThrow(() -> limiter.acquire("a"));
    }

    @Test
    void doesNotCountRejectedRequests() {
        AnalysisRateLimiter limiter = new AnalysisRateLimiter(2, 1, clock);
        limiter.acquire("a");
        assertThrows(TooManyAnalysesException.class, () -> limiter.acquire("a"));

        assertDoesNotThrow(() -> limiter.acquire("b"));
    }
}
