package com.bingchunmoli.oauth.client.redis;

import com.bingchunmoli.oauth.client.AuthorizedClientRef;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisRefreshCoordinatorTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final AuthorizedClientRef ref = new AuthorizedClientRef("test", "user");

    @Test void closedCoordinatorRejectsAcquisitionBeforeTouchingRedis() {
        var coordinator = new RedisRefreshCoordinator(redis, Duration.ofSeconds(1), Duration.ofSeconds(5));
        coordinator.close();
        assertThatThrownBy(() -> coordinator.acquire(ref)).isInstanceOf(IllegalStateException.class).hasMessageContaining("closed");
        verifyNoInteractions(redis);
    }

    @Test void shutdownWhileAcquiringReleasesTheLockAndClearsThreadOwnership() {
        when(redis.opsForValue()).thenReturn(values);
        var active = new AtomicReference<RedisRefreshCoordinator>();
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(invocation -> {
            active.get().close();
            return true;
        });
        try (var coordinator = new RedisRefreshCoordinator(redis, Duration.ofSeconds(1), Duration.ofSeconds(5))) {
            active.set(coordinator);
            assertThatThrownBy(() -> coordinator.acquire(ref)).isInstanceOf(IllegalStateException.class).hasMessageContaining("closed");
            assertThat(coordinator.ownership()).isNull();
            verify(redis).execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                    eq(List.of(RedisRefreshCoordinator.lockKey(ref))), anyString());
        }
    }

    @Test void slowAcquisitionCannotGrantALeaseAfterTheWaitTimeout() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(invocation -> {
            Thread.sleep(100);
            return true;
        });
        try (var coordinator = new RedisRefreshCoordinator(redis, Duration.ofMillis(30), Duration.ofSeconds(5))) {
            assertThatThrownBy(() -> coordinator.acquire(ref)).isInstanceOf(IllegalStateException.class).hasMessageContaining("timed out");
            assertThat(coordinator.ownership()).isNull();
            verify(redis).execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                    eq(List.of(RedisRefreshCoordinator.lockKey(ref))), anyString());
        }
    }
}
