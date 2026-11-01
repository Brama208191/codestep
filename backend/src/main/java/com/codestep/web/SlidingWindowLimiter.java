package com.codestep.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** At most {@code max} events per key in any sliding {@code window}, kept in memory. */
public final class SlidingWindowLimiter {

    private static final int PRUNE_ABOVE_KEYS = 10_000;

    private final int max;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> recent = new ConcurrentHashMap<>();

    public SlidingWindowLimiter(int max, Duration window, Clock clock) {
        this.max = max;
        this.window = window;
        this.clock = clock;
    }

    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        boolean[] allowed = {false};
        recent.compute(key, (k, times) -> {
            Deque<Instant> kept = times == null ? new ArrayDeque<>() : times;
            while (!kept.isEmpty() && !kept.peekFirst().isAfter(cutoff)) {
                kept.pollFirst();
            }
            if (kept.size() < max) {
                kept.addLast(now);
                allowed[0] = true;
            }
            return kept;
        });
        if (recent.size() > PRUNE_ABOVE_KEYS) {
            // Many one-off clients: drop keys with nothing left in the window.
            recent.values().removeIf(times -> times.isEmpty() || !times.peekLast().isAfter(cutoff));
        }
        return allowed[0];
    }
}
