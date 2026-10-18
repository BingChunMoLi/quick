package com.bingchunmoli.oauth.client.wechat;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@ConditionalOnProperty(prefix = "moli.oauth.client", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(WeChatOAuth2Properties.class)
public class WeChatOAuth2AutoConfiguration {
    @Bean @ConditionalOnMissingBean(name = "weChatOAuth2ProviderAdapter")
    WeChatOAuth2ProviderAdapter weChatOAuth2ProviderAdapter(WeChatOAuth2Properties properties, ObjectProvider<RestClient.Builder> builders) {
        return new WeChatOAuth2ProviderAdapter(false, properties, builders.getIfAvailable(RestClient::builder));
    }
    @Bean @ConditionalOnMissingBean(name = "weChatMpOAuth2ProviderAdapter")
    WeChatOAuth2ProviderAdapter weChatMpOAuth2ProviderAdapter(WeChatOAuth2Properties properties, ObjectProvider<RestClient.Builder> builders) {
        return new WeChatOAuth2ProviderAdapter(true, properties, builders.getIfAvailable(RestClient::builder));
    }
}
