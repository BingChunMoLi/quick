package com.bingchunmoli.web.autoconfigure;

import com.bingchunmoli.idempotency.exception.DuplicateRequestException;
import com.bingchunmoli.web.exception.DuplicateRequestExceptionHandler;
import com.bingchunmoli.web.exception.WebExceptionResponseFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Adds an HTTP 409 mapping when the idempotency starter is present.
 */
@AutoConfiguration(after = WebExceptionHandlerAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(DuplicateRequestException.class)
@ConditionalOnBean(WebExceptionResponseFactory.class)
@ConditionalOnProperty(prefix = "moli.web", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(
        prefix = "moli.web.exception-handling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyWebExceptionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DuplicateRequestExceptionHandler duplicateRequestExceptionHandler(
            WebExceptionResponseFactory responseFactory, QuickWebProperties properties) {
        return new DuplicateRequestExceptionHandler(responseFactory, properties);
    }
}
