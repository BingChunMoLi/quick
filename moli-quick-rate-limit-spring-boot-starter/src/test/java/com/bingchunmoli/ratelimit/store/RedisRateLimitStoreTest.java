package com.bingchunmoli.ratelimit.store;

import com.bingchunmoli.ratelimit.core.RateLimitDecision;
import com.bingchunmoli.ratelimit.core.RateLimitPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class RedisRateLimitStoreTest {

    @Test
    void shouldParseAllowedDecisionFromAtomicScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                any(RedisScript.class), eq(List.of("api:user-1")), eq("3"), eq("60000")))
                .thenReturn("1:2:60000");
        RedisRateLimitStore store = new RedisRateLimitStore(redisTemplate);

        assertThat(store.tryAcquire(
                "api:user-1", new RateLimitPolicy(3, Duration.ofMinutes(1))))
                .isEqualTo(new RateLimitDecision(true, 2, Duration.ofMinutes(1)));

        verify(redisTemplate).execute(
                any(RedisScript.class), eq(List.of("api:user-1")), eq("3"), eq("60000"));
    }

    @Test
    void shouldParseDeniedDecisionFromAtomicScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                any(RedisScript.class), eq(List.of("api:user-1")), eq("1"), eq("2000")))
                .thenReturn("0:0:1250");

        RateLimitDecision decision = new RedisRateLimitStore(redisTemplate)
                .tryAcquire("api:user-1", new RateLimitPolicy(1, Duration.ofSeconds(2)));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.remainingPermits()).isZero();
        assertThat(decision.resetAfter()).isEqualTo(Duration.ofMillis(1250));
    }
}
