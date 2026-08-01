package com.bingchunmoli.idempotency.store;

import com.bingchunmoli.idempotency.core.IdempotencyStore;
import com.bingchunmoli.idempotency.core.IdempotencyToken;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed store using SET NX with TTL and an ownership-safe Lua release.
 */
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final RedisScript<Long> RELEASE_SCRIPT = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisIdempotencyStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
    }

    @Override
    public Optional<IdempotencyToken> tryAcquire(String key, Duration timeout) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        IdempotencyToken token = new IdempotencyToken(key, UUID.randomUUID().toString());
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token.value(), timeout);
        return Boolean.TRUE.equals(acquired) ? Optional.of(token) : Optional.empty();
    }

    @Override
    public void release(IdempotencyToken token) {
        if (token != null) {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(token.key()), token.value());
        }
    }
}
