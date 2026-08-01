package com.bingchunmoli.idempotency.autoconfigure;

import com.bingchunmoli.idempotency.core.IdempotencyStore;
import com.bingchunmoli.idempotency.store.RedisIdempotencyStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.annotation.Bean;

/**
 * Redis provider for idempotency storage.
 */
@AutoConfiguration(
        afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
        before = IdempotencyAutoConfiguration.class)
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "moli.idempotency", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "moli.idempotency", name = "store", havingValue = "redis")
public class RedisIdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    public IdempotencyStore redisIdempotencyStore(StringRedisTemplate redisTemplate) {
        return new RedisIdempotencyStore(redisTemplate);
    }
}
