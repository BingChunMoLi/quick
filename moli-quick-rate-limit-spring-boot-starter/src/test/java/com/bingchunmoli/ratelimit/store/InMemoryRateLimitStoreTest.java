package com.bingchunmoli.ratelimit.store;

import com.bingchunmoli.ratelimit.core.RateLimitDecision;
import com.bingchunmoli.ratelimit.core.RateLimitPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimitStoreTest {

    @Test
    void shouldLimitWithinWindowAndResetAfterExpiry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryRateLimitStore store = new InMemoryRateLimitStore(clock);
        RateLimitPolicy policy = new RateLimitPolicy(2, Duration.ofMinutes(1));

        assertThat(store.tryAcquire("api:user-1", policy))
                .isEqualTo(new RateLimitDecision(true, 1, Duration.ofMinutes(1)));
        assertThat(store.tryAcquire("api:user-1", policy).allowed()).isTrue();
        RateLimitDecision denied = store.tryAcquire("api:user-1", policy);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.remainingPermits()).isZero();
        assertThat(denied.resetAfter()).isEqualTo(Duration.ofMinutes(1));

        clock.advance(Duration.ofSeconds(61));

        assertThat(store.tryAcquire("api:user-1", policy))
                .isEqualTo(new RateLimitDecision(true, 1, Duration.ofMinutes(1)));
    }

    @Test
    void shouldKeepDifferentKeysIndependent() {
        InMemoryRateLimitStore store = new InMemoryRateLimitStore();
        RateLimitPolicy policy = new RateLimitPolicy(1, Duration.ofMinutes(1));

        assertThat(store.tryAcquire("api:user-1", policy).allowed()).isTrue();
        assertThat(store.tryAcquire("api:user-1", policy).allowed()).isFalse();
        assertThat(store.tryAcquire("api:user-2", policy).allowed()).isTrue();
    }

    @Test
    void shouldPeriodicallyRemoveExpiredWindows() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryRateLimitStore store = new InMemoryRateLimitStore(clock);
        RateLimitPolicy policy = new RateLimitPolicy(1, Duration.ofSeconds(1));
        for (int index = 0; index < 255; index++) {
            assertThat(store.tryAcquire("expired:" + index, policy).allowed()).isTrue();
        }
        assertThat(store.windowCount()).isEqualTo(255);

        clock.advance(Duration.ofSeconds(2));
        assertThat(store.tryAcquire("current", policy).allowed()).isTrue();

        assertThat(store.windowCount()).isEqualTo(1);
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
