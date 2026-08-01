package com.bingchunmoli.ratelimit.autoconfigure;

import com.bingchunmoli.ratelimit.core.RateLimitStore;
import com.bingchunmoli.ratelimit.store.RedisRateLimitStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis provider for rate limit storage.
 */
@AutoConfiguration(
        afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
        before = RateLimitAutoConfiguration.class)
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "moli.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "moli.rate-limit", name = "store", havingValue = "redis")
public class RedisRateLimitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RateLimitStore.class)
    public RateLimitStore redisRateLimitStore(StringRedisTemplate redisTemplate) {
        return new RedisRateLimitStore(redisTemplate);
    }
}
