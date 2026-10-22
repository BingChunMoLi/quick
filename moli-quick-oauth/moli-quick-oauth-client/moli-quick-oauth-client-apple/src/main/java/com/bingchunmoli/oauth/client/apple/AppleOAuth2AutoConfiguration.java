package com.bingchunmoli.oauth.client.apple;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@ConditionalOnProperty(prefix = "moli.oauth.client", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AppleOAuth2Properties.class)
public class AppleOAuth2AutoConfiguration {
    @Bean @ConditionalOnMissingBean(AppleOAuth2ProviderAdapter.class)
    AppleOAuth2ProviderAdapter appleOAuth2ProviderAdapter(AppleOAuth2Properties properties,
            ObjectProvider<RestClient.Builder> builders) {
        return new AppleOAuth2ProviderAdapter(properties, builders.getIfAvailable(RestClient::builder));
    }
}
