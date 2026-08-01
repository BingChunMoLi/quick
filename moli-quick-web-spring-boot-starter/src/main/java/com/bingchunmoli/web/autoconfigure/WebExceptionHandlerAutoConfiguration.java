package com.bingchunmoli.web.autoconfigure;

import com.bingchunmoli.web.exception.DefaultWebExceptionResponseFactory;
import com.bingchunmoli.web.exception.GlobalWebExceptionHandler;
import com.bingchunmoli.web.exception.WebExceptionResponseFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Auto-configures the common Spring MVC exception response contract.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RestControllerAdvice.class)
@ConditionalOnProperty(prefix = "moli.web", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(
        prefix = "moli.web.exception-handling", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(QuickWebProperties.class)
public class WebExceptionHandlerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WebExceptionResponseFactory webExceptionResponseFactory() {
        return new DefaultWebExceptionResponseFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalWebExceptionHandler globalWebExceptionHandler(
            WebExceptionResponseFactory responseFactory, QuickWebProperties properties) {
        return new GlobalWebExceptionHandler(responseFactory, properties);
    }
}
