package com.bingchunmoli.oauth.client.microsoft;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@ConditionalOnProperty(prefix = "moli.oauth.client", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MicrosoftOAuth2Properties.class)
public class MicrosoftOAuth2AutoConfiguration {
    @Bean @ConditionalOnMissingBean(MicrosoftOAuth2ProviderAdapter.class)
    MicrosoftOAuth2ProviderAdapter microsoftOAuth2ProviderAdapter(MicrosoftOAuth2Properties properties,
            ObjectProvider<RestClient.Builder> builders) {
        return new MicrosoftOAuth2ProviderAdapter(properties, builders.getIfAvailable(RestClient::builder));
    }
}
