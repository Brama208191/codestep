package com.codestep.web;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowLimiterTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    @Test
    void allowsUpToTheLimitPerKeyThenRefuses() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(3, Duration.ofMinutes(1), clock);

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
        assertThat(limiter.tryAcquire("b")).as("other keys are independent").isTrue();
    }

    @Test
    void slotsFreeUpAsTheWindowSlides() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(2, Duration.ofMinutes(1), clock);
        limiter.tryAcquire("a");
        now.set(now.get().plusSeconds(30));
        limiter.tryAcquire("a");

        assertThat(limiter.tryAcquire("a")).isFalse();
        now.set(now.get().plusSeconds(31));
        assertThat(limiter.tryAcquire("a")).as("the first event left the window").isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
    }

    @Test
    void refusedAttemptsDoNotExtendTheWait() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(1, Duration.ofMinutes(1), clock);
        limiter.tryAcquire("a");
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("a");
        }

        now.set(now.get().plusSeconds(61));
        assertThat(limiter.tryAcquire("a")).isTrue();
    }
}
