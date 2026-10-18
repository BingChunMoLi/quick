package com.bingchunmoli.oauth.client.autoconfigure;

import com.bingchunmoli.oauth.client.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.*;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.client.registration.*;

/** Runs before Boot's native registration/service defaults, but honors host beans. */
@AutoConfiguration(before = OAuth2ClientAutoConfiguration.class,
        afterName = {"com.bingchunmoli.oauth.client.github.GitHubOAuth2AutoConfiguration",
                "com.bingchunmoli.oauth.client.jdbc.JdbcOAuth2AutoConfiguration",
                "com.bingchunmoli.oauth.client.redis.RedisOAuth2AutoConfiguration"})
@ConditionalOnProperty(prefix = "moli.oauth.client", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({QuickOAuth2Properties.class, OAuth2ClientProperties.class})
public class QuickOAuth2AutoConfiguration {
    @Bean @ConditionalOnMissingBean
    OAuth2ProviderRegistry oauth2ProviderRegistry(ObjectProvider<OAuth2ProviderAdapter> adapters,
            OAuth2ClientProperties nativeProperties, QuickOAuth2Properties quick) {
        Map<String, String> mappings = new HashMap<>();
        nativeProperties.getRegistration().forEach((id, registration) -> {
            if (registration.getProvider() != null) mappings.put(id, registration.getProvider());
        });
        mappings.putAll(quick.getProviderMappings());
        return new OAuth2ProviderRegistry(adapters.orderedStream().toList(), mappings);
    }

    @Bean @ConditionalOnMissingBean(ClientRegistrationRepository.class)
    ClientRegistrationRepository quickClientRegistrationRepository(OAuth2ClientProperties properties,
            OAuth2ProviderRegistry registry) {
        properties.getRegistration().forEach((id, registration) -> {
            OAuth2ProviderAdapter adapter = registry.adapter(id);
            if (adapter == null) return;
            Map<String, String> d = adapter.defaults();
            String providerId = registration.getProvider() == null ? id : registration.getProvider();
            var provider = properties.getProvider().computeIfAbsent(providerId, key -> new OAuth2ClientProperties.Provider());
            registration.setProvider(providerId);
            if (provider.getAuthorizationUri() == null) provider.setAuthorizationUri(d.get("authorization-uri"));
            if (provider.getTokenUri() == null) provider.setTokenUri(d.get("token-uri"));
            if (provider.getUserInfoUri() == null) provider.setUserInfoUri(d.get("user-info-uri"));
            if (provider.getUserNameAttribute() == null) provider.setUserNameAttribute(d.get("user-name-attribute"));
            if (registration.getAuthorizationGrantType() == null) registration.setAuthorizationGrantType(d.get("authorization-grant-type"));
            if (registration.getClientAuthenticationMethod() == null) registration.setClientAuthenticationMethod(d.get("client-authentication-method"));
            if (registration.getRedirectUri() == null) registration.setRedirectUri(d.get("redirect-uri"));
            if (registration.getScope() == null && d.containsKey("scope")) registration.setScope(Set.of(d.get("scope").split(",")));
        });
        var registrations = new OAuth2ClientPropertiesMapper(properties).asClientRegistrations();
        if (registrations.isEmpty()) throw new IllegalStateException("At least one OAuth2 client registration is required");
        registrations.values().forEach(registration -> validateRegistration(registration, registry));
        return new InMemoryClientRegistrationRepository(registrations);
    }

    @Bean
    SmartInitializingSingleton quickRegistrationValidation(ClientRegistrationRepository registrations,
            OAuth2ProviderRegistry registry, OAuth2ClientProperties nativeProperties, QuickOAuth2Properties quick) {
        return () -> {
            if (registrations instanceof Iterable<?> iterable) {
                for (Object registration : iterable) validateRegistration((ClientRegistration) registration, registry);
            } else {
                Set<String> ids = new HashSet<>(nativeProperties.getRegistration().keySet());
                ids.addAll(quick.getProviderMappings().keySet());
                for (String id : ids) {
                    ClientRegistration registration = registrations.findByRegistrationId(id);
                    if (registration != null) validateRegistration(registration, registry);
                }
            }
        };
    }

    private static void validateRegistration(ClientRegistration registration, OAuth2ProviderRegistry registry) {
        var adapter = registry.adapter(registration.getRegistrationId());
        var supported = adapter == null ? Set.of(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE,
                org.springframework.security.oauth2.core.AuthorizationGrantType.CLIENT_CREDENTIALS) : adapter.supportedGrantTypes();
        if (!supported.contains(registration.getAuthorizationGrantType())) {
            throw new IllegalStateException("Unsupported grant type for OAuth2 registration: " + registration.getRegistrationId());
        }
    }

    @Bean @ConditionalOnMissingBean(OAuth2AuthorizedClientService.class)
    OAuth2AuthorizedClientService quickAuthorizedClientService(ClientRegistrationRepository registrations,
                                                               QuickOAuth2Properties properties) {
        if (!"memory".equals(properties.getAuthorizedClient().getStore())) {
            throw new IllegalStateException("Selected OAuth2 authorized-client store requires its module and connection bean");
        }
        return new InMemoryOAuth2AuthorizedClientService(registrations);
    }

    @Bean @ConditionalOnMissingBean(RefreshCoordinator.class)
    RefreshCoordinator quickRefreshCoordinator(QuickOAuth2Properties properties) {
        String mode = properties.getRefresh().getCoordination();
        if (!Set.of("auto", "local", "redis").contains(mode)) throw new IllegalStateException("Unknown OAuth2 refresh coordination");
        if ("redis".equals(mode) || ("auto".equals(mode) && "redis".equals(properties.getAuthorizedClient().getStore()))) {
            throw new IllegalStateException("Redis refresh coordination requires the Redis module and StringRedisTemplate");
        }
        return new LocalRefreshCoordinator(properties.getRefresh().getWaitTimeout());
    }

    @Bean @ConditionalOnMissingBean
    ProviderTokenClients quickProviderTokenClients(OAuth2ProviderRegistry registry,
            ObjectProvider<OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>> code,
            ObjectProvider<OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest>> refresh) {
        return new ProviderTokenClients(registry, code.getIfAvailable(RestClientAuthorizationCodeTokenResponseClient::new),
                refresh.getIfAvailable(RestClientRefreshTokenTokenResponseClient::new));
    }

    @Bean @ConditionalOnMissingBean(OAuth2AuthorizedClientProvider.class)
    OAuth2AuthorizedClientProvider quickAuthorizedClientProvider(ProviderTokenClients tokenClients,
            ObjectProvider<OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest>> credentials) {
        return OAuth2AuthorizedClientProviderBuilder.builder()
                .refreshToken(refresh -> refresh.accessTokenResponseClient(tokenClients.refreshToken()))
                .clientCredentials(client -> client.accessTokenResponseClient(
                        credentials.getIfAvailable(RestClientClientCredentialsTokenResponseClient::new))).build();
    }

    @Bean @ConditionalOnMissingBean(OAuth2AuthorizedClientManager.class)
    OAuth2AuthorizedClientManager quickAuthorizedClientManager(ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService service, OAuth2AuthorizedClientProvider provider, RefreshCoordinator coordinator) {
        return new CoordinatedAuthorizedClientManager(registrations, service, provider, coordinator);
    }

    @Bean @ConditionalOnMissingBean
    QuickOAuth2Client quickOAuth2Client(OAuth2AuthorizedClientManager manager, OAuth2AuthorizedClientService service,
                                      RefreshCoordinator coordinator) {
        return new QuickOAuth2Client(manager, service, coordinator);
    }
}
