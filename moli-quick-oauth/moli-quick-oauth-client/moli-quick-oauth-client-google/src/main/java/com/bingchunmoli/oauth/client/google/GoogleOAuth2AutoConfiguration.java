package com.bingchunmoli.oauth.client.google;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "moli.oauth.client", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(GoogleOAuth2Properties.class)
public class GoogleOAuth2AutoConfiguration {
    @Bean @ConditionalOnMissingBean(GoogleOAuth2ProviderAdapter.class)
    GoogleOAuth2ProviderAdapter googleOAuth2ProviderAdapter(GoogleOAuth2Properties properties) {
        return new GoogleOAuth2ProviderAdapter(properties);
    }
}
