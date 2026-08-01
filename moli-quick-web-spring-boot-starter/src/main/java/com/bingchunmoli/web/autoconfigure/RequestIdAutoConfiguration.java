package com.bingchunmoli.web.autoconfigure;

import com.bingchunmoli.web.request.RequestIdFilter;
import com.bingchunmoli.web.request.RequestIdGenerator;
import com.bingchunmoli.web.request.UuidRequestIdGenerator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Auto-configures request identifier propagation for Servlet applications.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OncePerRequestFilter.class)
@ConditionalOnProperty(prefix = "moli.web", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(
        prefix = "moli.web.request-id", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(QuickWebProperties.class)
public class RequestIdAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RequestIdGenerator requestIdGenerator() {
        return new UuidRequestIdGenerator();
    }

    @Bean
    @ConditionalOnMissingBean(name = "moliRequestIdFilterRegistration")
    public FilterRegistrationBean<RequestIdFilter> moliRequestIdFilterRegistration(
            QuickWebProperties properties, RequestIdGenerator generator) {
        RequestIdFilter filter = new RequestIdFilter(properties.getRequestId(), generator);
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setName("moliRequestIdFilter");
        registration.setOrder(properties.getRequestId().getFilterOrder());
        return registration;
    }
}
