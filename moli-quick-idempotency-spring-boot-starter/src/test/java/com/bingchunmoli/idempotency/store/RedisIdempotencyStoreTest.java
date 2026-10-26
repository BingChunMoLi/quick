package com.bingchunmoli.idempotency.store;

import com.bingchunmoli.idempotency.core.IdempotencyToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisIdempotencyStoreTest {

    @ParameterizedTest
    @ValueSource(longs = {1, 100_000, 999_999, 1_000_000})
    void shouldUseAtLeastOneMillisecondForPositiveTimeouts(long nanos) {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        IdempotencyToken token = new RedisIdempotencyStore(redisTemplate)
                .tryAcquire("key", Duration.ofNanos(nanos)).orElseThrow();

        verify(valueOperations).setIfAbsent("key", token.value(), Duration.ofMillis(1));
    }

    @Test
    void shouldAcquireWithTtlAndReleaseThroughScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        RedisIdempotencyStore store = new RedisIdempotencyStore(redisTemplate);

        IdempotencyToken token = store.tryAcquire("key", Duration.ofMinutes(2)).orElseThrow();
        store.release(token);

        assertThat(token.key()).isEqualTo("key");
        verify(valueOperations).setIfAbsent("key", token.value(), Duration.ofMinutes(2));
        verify(redisTemplate).execute(any(), any(), anyString());
    }

    @Test
    void shouldRejectWhenRedisKeyAlreadyExists() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertThat(new RedisIdempotencyStore(redisTemplate)
                .tryAcquire("key", Duration.ofMinutes(1))).isEmpty();
    }
}
