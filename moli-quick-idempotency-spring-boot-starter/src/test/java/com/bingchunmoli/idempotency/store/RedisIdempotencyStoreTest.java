package com.bingchunmoli.idempotency.store;

import com.bingchunmoli.idempotency.core.IdempotencyToken;
import org.junit.jupiter.api.Test;
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
