package com.bingchunmoli.oauth.client.github;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@ConditionalOnProperty(prefix = "moli.oauth.client", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(GitHubOAuth2Properties.class)
public class GitHubOAuth2AutoConfiguration {
    @Bean @ConditionalOnMissingBean(GitHubOAuth2ProviderAdapter.class)
    GitHubOAuth2ProviderAdapter githubOAuth2ProviderAdapter(GitHubOAuth2Properties properties,
                                                           ObjectProvider<RestClient.Builder> builders) {
        return new GitHubOAuth2ProviderAdapter(properties, builders.getIfAvailable(RestClient::builder));
    }
}
