package com.bingchunmoli.web.autoconfigure;

import com.bingchunmoli.ratelimit.exception.RateLimitExceededException;
import com.bingchunmoli.web.exception.RateLimitExceptionHandler;
import com.bingchunmoli.web.exception.WebExceptionResponseFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Adds an HTTP 429 mapping when the rate limit starter is present.
 */
@AutoConfiguration(after = WebExceptionHandlerAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RateLimitExceededException.class)
@ConditionalOnBean(WebExceptionResponseFactory.class)
@ConditionalOnProperty(prefix = "moli.web", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(
        prefix = "moli.web.exception-handling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitWebExceptionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RateLimitExceptionHandler rateLimitExceptionHandler(
            WebExceptionResponseFactory responseFactory, QuickWebProperties properties) {
        return new RateLimitExceptionHandler(responseFactory, properties);
    }
}
