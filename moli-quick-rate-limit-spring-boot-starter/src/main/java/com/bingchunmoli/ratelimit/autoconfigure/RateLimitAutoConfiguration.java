package com.bingchunmoli.ratelimit.autoconfigure;

import com.bingchunmoli.ratelimit.aspect.RateLimitAspect;
import com.bingchunmoli.ratelimit.core.RateLimitKeyResolver;
import com.bingchunmoli.ratelimit.core.RateLimitStore;
import com.bingchunmoli.ratelimit.store.InMemoryRateLimitStore;
import com.bingchunmoli.ratelimit.support.DefaultRateLimitKeyResolver;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Core auto-configuration for annotation-driven rate limiting.
 */
@AutoConfiguration(after = RedisRateLimitAutoConfiguration.class)
@ConditionalOnClass(Aspect.class)
@ConditionalOnProperty(prefix = "moli.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RateLimitStore.class)
    public RateLimitStore rateLimitStore(RateLimitProperties properties) {
        if (properties.getStore() == RateLimitProperties.Store.REDIS) {
            throw new IllegalStateException(
                    "moli.rate-limit.store=redis requires an auto-configured StringRedisTemplate");
        }
        return new InMemoryRateLimitStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitKeyResolver rateLimitKeyResolver(RateLimitProperties properties) {
        return new DefaultRateLimitKeyResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitAspect rateLimitAspect(RateLimitStore store, RateLimitKeyResolver keyResolver,
                                           RateLimitProperties properties) {
        return new RateLimitAspect(store, keyResolver, properties);
    }
}
