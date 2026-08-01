package com.bingchunmoli.idempotency.autoconfigure;

import com.bingchunmoli.idempotency.aspect.IdempotencyAspect;
import com.bingchunmoli.idempotency.core.IdempotencyKeyResolver;
import com.bingchunmoli.idempotency.core.IdempotencyStore;
import com.bingchunmoli.idempotency.store.InMemoryIdempotencyStore;
import com.bingchunmoli.idempotency.support.DefaultIdempotencyKeyResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Core auto-configuration for annotation-driven idempotency.
 */
@AutoConfiguration(after = RedisIdempotencyAutoConfiguration.class)
@ConditionalOnClass(Aspect.class)
@ConditionalOnProperty(prefix = "moli.idempotency", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    public IdempotencyStore idempotencyStore(IdempotencyProperties properties) {
        if (properties.getStore() == IdempotencyProperties.Store.REDIS) {
            throw new IllegalStateException(
                    "moli.idempotency.store=redis requires an auto-configured StringRedisTemplate");
        }
        return new InMemoryIdempotencyStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyKeyResolver idempotencyKeyResolver(ObjectProvider<ObjectMapper> objectMapper,
                                                          IdempotencyProperties properties) {
        return new DefaultIdempotencyKeyResolver(objectMapper.getIfAvailable(ObjectMapper::new), properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyAspect idempotencyAspect(IdempotencyStore store, IdempotencyKeyResolver keyResolver,
                                               IdempotencyProperties properties) {
        return new IdempotencyAspect(store, keyResolver, properties);
    }
}
