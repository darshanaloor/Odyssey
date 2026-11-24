package com.odyssey.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MarketRateLimiter {

    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    private final ConcurrentHashMap<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    public boolean isAllowed(String userId) {
        if (userId == null || userId.isBlank()) {
            userId = "anonymous";
        }
        long now = Instant.now().toEpochMilli();
        long windowStart = now - 60_000L;

        Deque<Long> timestamps = requestLog.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    public void checkLimit(String userId) {
        if (!isAllowed(userId)) {
            throw new com.odyssey.exception.RateLimitException("Search rate limit exceeded (60 requests/minute). Please try again shortly.");
        }
    }
}
