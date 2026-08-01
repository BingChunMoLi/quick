package com.bingchunmoli.idempotency.store;

import com.bingchunmoli.idempotency.core.IdempotencyToken;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryIdempotencyStoreTest {

    @Test
    void shouldRejectDuplicateUntilReleased() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
        IdempotencyToken token = store.tryAcquire("order:1", Duration.ofMinutes(1)).orElseThrow();

        assertThat(store.tryAcquire("order:1", Duration.ofMinutes(1))).isEmpty();

        store.release(token);
        assertThat(store.tryAcquire("order:1", Duration.ofMinutes(1))).isPresent();
    }

    @Test
    void shouldReplaceExpiredEntryAndProtectNewOwner() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(clock);
        IdempotencyToken expiredOwner = store.tryAcquire("order:2", Duration.ofSeconds(1)).orElseThrow();

        clock.advance(Duration.ofSeconds(2));
        IdempotencyToken currentOwner = store.tryAcquire("order:2", Duration.ofMinutes(1)).orElseThrow();
        store.release(expiredOwner);

        assertThat(store.tryAcquire("order:2", Duration.ofMinutes(1))).isEmpty();
        store.release(currentOwner);
        assertThat(store.tryAcquire("order:2", Duration.ofMinutes(1))).isPresent();
    }

    @Test
    void shouldPeriodicallyRemoveExpiredEntries() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(clock);
        for (int index = 0; index < 255; index++) {
            assertThat(store.tryAcquire("expired:" + index, Duration.ofSeconds(1))).isPresent();
        }
        assertThat(store.entryCount()).isEqualTo(255);

        clock.advance(Duration.ofSeconds(2));
        assertThat(store.tryAcquire("current", Duration.ofMinutes(1))).isPresent();

        assertThat(store.entryCount()).isEqualTo(1);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
