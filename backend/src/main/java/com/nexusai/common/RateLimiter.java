package com.nexusai.common;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador de janela deslizante em memoria (suficiente para um unico no; ver docs/DECISIONS.md).
 */
public class RateLimiter {

    private final int maxEvents;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Long>> events = new ConcurrentHashMap<>();

    public RateLimiter(int maxEvents, long windowMillis, Clock clock) {
        this.maxEvents = maxEvents;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    /** Registra uma tentativa; retorna false se o limite foi excedido. */
    public boolean tryAcquire(String key) {
        long now = clock.millis();
        Deque<Long> q = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() >= windowMillis) {
                q.pollFirst();
            }
            if (q.size() >= maxEvents) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }

    public void reset(String key) {
        events.remove(key);
    }
}
