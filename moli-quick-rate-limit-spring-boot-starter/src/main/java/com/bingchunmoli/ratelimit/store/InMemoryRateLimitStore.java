package com.bingchunmoli.ratelimit.store;

import com.bingchunmoli.ratelimit.core.RateLimitDecision;
import com.bingchunmoli.ratelimit.core.RateLimitPolicy;
import com.bingchunmoli.ratelimit.core.RateLimitStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-local fixed-window store, suitable for single-instance applications and tests.
 */
public class InMemoryRateLimitStore implements RateLimitStore {

    private static final int CLEANUP_INTERVAL = 256;

    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicInteger operations = new AtomicInteger();
    private final Clock clock;

    public InMemoryRateLimitStore() {
        this(Clock.systemUTC());
    }

    InMemoryRateLimitStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public RateLimitDecision tryAcquire(String key, RateLimitPolicy policy) {
        validateKey(key);
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        Instant now = clock.instant();
        cleanupExpiredWindowsPeriodically(now);
        AtomicReference<RateLimitDecision> decision = new AtomicReference<>();
        windows.compute(key, (ignored, existing) -> updateWindow(existing, policy, now, decision));
        return decision.get();
    }

    private Window updateWindow(Window existing, RateLimitPolicy policy, Instant now,
                                AtomicReference<RateLimitDecision> decision) {
        if (existing == null || !existing.expiresAt().isAfter(now)) {
            Instant expiresAt = now.plus(policy.window());
            decision.set(new RateLimitDecision(true, policy.permits() - 1, policy.window()));
            return new Window(1, expiresAt);
        }

        Duration resetAfter = Duration.between(now, existing.expiresAt());
        if (existing.consumedPermits() >= policy.permits()) {
            decision.set(new RateLimitDecision(false, 0, resetAfter));
            return existing;
        }

        long consumedPermits = existing.consumedPermits() + 1;
        decision.set(new RateLimitDecision(true, policy.permits() - consumedPermits, resetAfter));
        return new Window(consumedPermits, existing.expiresAt());
    }

    private void cleanupExpiredWindowsPeriodically(Instant now) {
        if ((operations.incrementAndGet() & (CLEANUP_INTERVAL - 1)) != 0) {
            return;
        }
        windows.forEach((key, window) -> {
            if (!window.expiresAt().isAfter(now)) {
                windows.remove(key, window);
            }
        });
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
    }

    int windowCount() {
        return windows.size();
    }

    private record Window(long consumedPermits, Instant expiresAt) {
    }
}
