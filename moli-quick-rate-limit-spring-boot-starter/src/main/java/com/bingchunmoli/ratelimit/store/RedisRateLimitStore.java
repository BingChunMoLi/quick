package com.bingchunmoli.ratelimit.store;

import com.bingchunmoli.ratelimit.core.RateLimitDecision;
import com.bingchunmoli.ratelimit.core.RateLimitPolicy;
import com.bingchunmoli.ratelimit.core.RateLimitStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Redis fixed-window store implemented as one atomic Lua operation.
 */
public class RedisRateLimitStore implements RateLimitStore {

    private static final RedisScript<String> ACQUIRE_SCRIPT = RedisScript.of(
            "local limit = tonumber(ARGV[1]) "
                    + "local window = tonumber(ARGV[2]) "
                    + "local current = redis.call('get', KEYS[1]) "
                    + "if not current then "
                    + "redis.call('psetex', KEYS[1], window, 1) "
                    + "return '1:' .. (limit - 1) .. ':' .. window "
                    + "end "
                    + "local ttl = redis.call('pttl', KEYS[1]) "
                    + "if ttl < 0 then redis.call('pexpire', KEYS[1], window) ttl = window end "
                    + "current = tonumber(current) "
                    + "if current >= limit then return '0:0:' .. ttl end "
                    + "current = redis.call('incr', KEYS[1]) "
                    + "return '1:' .. (limit - current) .. ':' .. ttl",
            String.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
    }

    @Override
    public RateLimitDecision tryAcquire(String key, RateLimitPolicy policy) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        long windowMillis = Math.max(1, policy.window().toMillis());
        String result = redisTemplate.execute(
                ACQUIRE_SCRIPT,
                List.of(key),
                Long.toString(policy.permits()),
                Long.toString(windowMillis));
        return parseDecision(result);
    }

    private RateLimitDecision parseDecision(String result) {
        if (result == null) {
            throw new IllegalStateException("Redis rate limit script returned no result");
        }
        String[] parts = result.split(":", -1);
        if (parts.length != 3) {
            throw new IllegalStateException("Unexpected Redis rate limit result: " + result);
        }
        try {
            boolean allowed = "1".equals(parts[0]);
            long remainingPermits = Long.parseLong(parts[1]);
            long resetAfterMillis = Math.max(0, Long.parseLong(parts[2]));
            return new RateLimitDecision(allowed, remainingPermits, Duration.ofMillis(resetAfterMillis));
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Unexpected Redis rate limit result: " + result, ex);
        }
    }
}
