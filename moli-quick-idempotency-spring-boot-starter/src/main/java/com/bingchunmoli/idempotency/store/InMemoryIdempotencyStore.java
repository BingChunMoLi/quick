package com.bingchunmoli.idempotency.store;

import com.bingchunmoli.idempotency.core.IdempotencyStore;
import com.bingchunmoli.idempotency.core.IdempotencyToken;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-local idempotency store, suitable for single-instance applications and tests.
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private static final int CLEANUP_INTERVAL = 256;

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicInteger operations = new AtomicInteger();
    private final Clock clock;

    public InMemoryIdempotencyStore() {
        this(Clock.systemUTC());
    }

    InMemoryIdempotencyStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<IdempotencyToken> tryAcquire(String key, Duration timeout) {
        validate(key, timeout);
        Instant now = clock.instant();
        cleanupExpiredEntriesPeriodically(now);
        AtomicReference<Entry> acquired = new AtomicReference<>();
        entries.compute(key, (ignored, existing) -> {
            if (existing == null || !existing.expiresAt().isAfter(now)) {
                IdempotencyToken token = new IdempotencyToken(key, UUID.randomUUID().toString());
                Entry replacement = new Entry(token, now.plus(timeout));
                acquired.set(replacement);
                return replacement;
            }
            return existing;
        });
        Entry entry = acquired.get();
        return entry == null ? Optional.empty() : Optional.of(entry.token());
    }

    @Override
    public void release(IdempotencyToken token) {
        if (token != null) {
            entries.computeIfPresent(token.key(), (ignored, existing) ->
                    existing.token().value().equals(token.value()) ? null : existing);
        }
    }

    private void validate(String key, Duration timeout) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    private void cleanupExpiredEntriesPeriodically(Instant now) {
        if ((operations.incrementAndGet() & (CLEANUP_INTERVAL - 1)) != 0) {
            return;
        }
        entries.forEach((key, entry) -> {
            if (!entry.expiresAt().isAfter(now)) {
                entries.remove(key, entry);
            }
        });
    }

    int entryCount() {
        return entries.size();
    }

    private record Entry(IdempotencyToken token, Instant expiresAt) {
    }
}
