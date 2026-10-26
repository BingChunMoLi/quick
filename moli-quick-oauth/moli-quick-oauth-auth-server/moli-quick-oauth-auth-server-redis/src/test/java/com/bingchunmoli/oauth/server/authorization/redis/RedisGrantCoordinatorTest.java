package com.bingchunmoli.oauth.server.authorization.redis;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisGrantCoordinatorTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final Duration lease = Duration.ofMinutes(5);

    @Test
    void businessExecutorRejectionKeepsItsOriginalExceptionAndReleasesTheLock() {
        when(redis.opsForValue()).thenReturn(values);
        var owner = new AtomicReference<String>();
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(call -> {
            owner.set(call.getArgument(1));
            return true;
        });
        when(values.get(anyString())).thenAnswer(call -> owner.get());
        var failure = new RejectedExecutionException("business executor rejected");
        try (var coordinator = new RedisGrantCoordinator(redis, "test:grant", Duration.ofSeconds(5), lease)) {
            assertThatThrownBy(() -> coordinator.execute("authorization", () -> { throw failure; }))
                    .isSameAs(failure);
            verify(redis).execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                    eq(List.of(coordinator.lockKey("authorization"))), anyString());
            assertThat(coordinator.execute("different", () -> "next")).isEqualTo("next");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nestedWorkPreservesItsOutcomeAndReleasesThreadOwnership(boolean failing) {
        when(redis.opsForValue()).thenReturn(values);
        var owner = new AtomicReference<String>();
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(call -> {
            owner.set(call.getArgument(1));
            return true;
        });
        when(values.get(anyString())).thenAnswer(call -> owner.get());
        var original = new IllegalArgumentException("business failure");
        try (var coordinator = new RedisGrantCoordinator(redis, "test:grant", Duration.ofSeconds(5), lease)) {
            java.util.function.Supplier<String> work = () -> coordinator.execute("authorization", () -> {
                if (failing) throw original;
                return "issued";
            });
            if (failing) assertThatThrownBy(() -> coordinator.execute("authorization", work)).isSameAs(original);
            else assertThat(coordinator.execute("authorization", work)).isEqualTo("issued");
            assertThat(coordinator.execute("different", () -> "next")).isEqualTo("next");
            verify(values, times(2)).setIfAbsent(anyString(), anyString(), eq(lease));
            verify(redis).execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                    eq(List.of(coordinator.lockKey("authorization"))), anyString());
            verify(redis).execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                    eq(List.of(coordinator.lockKey("different"))), anyString());
        }
    }

    @Test
    void closedCoordinatorRejectsWorkBeforeTouchingRedis() {
        var coordinator = new RedisGrantCoordinator(redis, "test:grant", Duration.ofSeconds(5), lease);
        coordinator.close();
        assertThatThrownBy(() -> coordinator.execute("authorization", () -> "issued"))
                .isInstanceOf(OAuth2AuthenticationException.class);
        verifyNoInteractions(redis);
    }

    @Test
    void shutdownDuringAcquisitionReleasesTheLockWithoutIssuingTokens() {
        when(redis.opsForValue()).thenReturn(values);
        var active = new AtomicReference<RedisGrantCoordinator>();
        var issued = new AtomicBoolean();
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(call -> {
            active.get().close();
            return true;
        });
        try (var coordinator = new RedisGrantCoordinator(redis, "test:grant", Duration.ofSeconds(5), lease)) {
            active.set(coordinator);
            assertThatThrownBy(() -> coordinator.execute("authorization", () -> issued.getAndSet(true)))
                    .isInstanceOf(OAuth2AuthenticationException.class);
            assertThat(issued).isFalse();
            verify(redis).execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                    eq(List.of(coordinator.lockKey("authorization"))), anyString());
        }
    }

    @Test
    void slowSuccessfulAcquisitionCannotIssueTokensAfterTheWaitTimeout() {
        when(redis.opsForValue()).thenReturn(values);
        var owner = new AtomicReference<String>();
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(call -> {
            owner.set(call.getArgument(1));
            Thread.sleep(100);
            return true;
        });
        when(values.get(anyString())).thenAnswer(call -> owner.get());
        var issued = new AtomicBoolean();
        try (var coordinator = new RedisGrantCoordinator(redis, "test:grant", Duration.ofMillis(30), lease)) {
            assertThatThrownBy(() -> coordinator.execute("authorization", () -> issued.getAndSet(true)))
                    .isInstanceOf(OAuth2AuthenticationException.class);
            assertThat(issued).isFalse();
            verify(redis).execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                    eq(List.of(coordinator.lockKey("authorization"))), anyString());
        }
    }
}
