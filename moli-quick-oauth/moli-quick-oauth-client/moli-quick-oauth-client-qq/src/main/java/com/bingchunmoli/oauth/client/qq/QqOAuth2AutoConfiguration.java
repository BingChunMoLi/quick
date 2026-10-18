package com.bingchunmoli.oauth.client.qq;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@ConditionalOnProperty(prefix = "moli.oauth.client", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(QqOAuth2Properties.class)
public class QqOAuth2AutoConfiguration {
    @Bean @ConditionalOnMissingBean(QqOAuth2ProviderAdapter.class)
    QqOAuth2ProviderAdapter qqOAuth2ProviderAdapter(QqOAuth2Properties properties, ObjectProvider<RestClient.Builder> builders) {
        return new QqOAuth2ProviderAdapter(properties, builders.getIfAvailable(RestClient::builder));
    }
}
