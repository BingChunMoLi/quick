package com.bingchunmoli.oauth.client.autoconfigure;

import com.bingchunmoli.oauth.client.OAuth2ProviderRegistry;
import com.bingchunmoli.oauth.client.oidc.OidcProviderAdapter;
import java.util.*;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** One current decoder per registration ID, with bounded retention for dynamic repositories. */
final class ProviderOidcIdTokenDecoderFactory implements JwtDecoderFactory<ClientRegistration> {
    private static final int CAPACITY = 256;
    private final Map<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final OAuth2ProviderRegistry registry;
    private final OidcIdTokenDecoderFactory fallback = new OidcIdTokenDecoderFactory();
    private final JsonMapper json = JsonMapper.builder().build();

    ProviderOidcIdTokenDecoderFactory(OAuth2ProviderRegistry registry) { this.registry = registry; }

    @Override public JwtDecoder createDecoder(ClientRegistration registration) {
        Entry entry;
        synchronized (entries) {
            entry = entries.computeIfAbsent(registration.getRegistrationId(), id -> new Entry());
            if (entries.size() > CAPACITY) entries.remove(entries.keySet().iterator().next());
        }
        return entry.resolve(registration);
    }

    private final class Entry {
        private Configuration configuration;
        private JwtDecoder decoder;
        synchronized JwtDecoder resolve(ClientRegistration registration) {
            Configuration current = snapshot(registration);
            if (!current.equals(configuration)) {
                var adapter = registry.adapter(registration.getRegistrationId());
                var custom = adapter instanceof OidcProviderAdapter oidc ? oidc.idTokenDecoder(registration) : null;
                JwtDecoder replacement = custom == null ? fallback.createDecoder(registration) : custom;
                configuration = current;
                decoder = replacement;
            }
            return decoder;
        }
    }

    private Configuration snapshot(ClientRegistration registration) {
        var provider = registration.getProviderDetails();
        var userInfo = provider.getUserInfoEndpoint();
        // Discovery metadata is JSON; copying it also detects changes to nested maps/lists.
        JsonNode metadata = json.valueToTree(provider.getConfigurationMetadata());
        return new Configuration(registration.getClientId(), registration.getClientSecret(),
                registration.getClientAuthenticationMethod(), registration.getAuthorizationGrantType(),
                registration.getRedirectUri(), Set.copyOf(registration.getScopes()), registration.getClientName(),
                registration.getClientSettings(), provider.getAuthorizationUri(), provider.getTokenUri(),
                provider.getJwkSetUri(), provider.getIssuerUri(), userInfo.getUri(), userInfo.getAuthenticationMethod(),
                userInfo.getUserNameAttributeName(), metadata);
    }

    private record Configuration(String clientId, String clientSecret,
            org.springframework.security.oauth2.core.ClientAuthenticationMethod clientAuthenticationMethod,
            org.springframework.security.oauth2.core.AuthorizationGrantType grantType, String redirectUri, Set<String> scopes,
            String clientName, ClientRegistration.ClientSettings clientSettings, String authorizationUri, String tokenUri,
            String jwkSetUri, String issuerUri, String userInfoUri,
            org.springframework.security.oauth2.core.AuthenticationMethod userInfoAuthenticationMethod,
            String userNameAttribute, JsonNode metadata) { }
}
