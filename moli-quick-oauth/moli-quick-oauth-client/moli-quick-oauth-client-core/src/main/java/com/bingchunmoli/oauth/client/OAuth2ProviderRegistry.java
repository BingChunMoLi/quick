package com.bingchunmoli.oauth.client;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

public final class OAuth2ProviderRegistry {
    private final Map<String, OAuth2ProviderAdapter> adapters = new LinkedHashMap<>();
    private final Map<String, String> providers;

    public OAuth2ProviderRegistry(Collection<OAuth2ProviderAdapter> adapters, Map<String, String> providers) {
        for (OAuth2ProviderAdapter adapter : adapters) {
            if (this.adapters.putIfAbsent(adapter.id(), adapter) != null) {
                throw new IllegalArgumentException("Duplicate OAuth2 provider: " + adapter.id());
            }
        }
        this.providers = Map.copyOf(providers);
    }

    public Collection<OAuth2ProviderAdapter> adapters() { return java.util.List.copyOf(adapters.values()); }
    public OAuth2ProviderAdapter adapter(String registrationId) {
        return adapters.get(providers.getOrDefault(registrationId, registrationId));
    }
    public OAuth2Identity identity(ClientRegistration registration, OAuth2User user) {
        return identity(registration, user, Map.of());
    }
    public OAuth2Identity identity(ClientRegistration registration, OAuth2User user, Map<String, String[]> callback) {
        OAuth2ProviderAdapter adapter = adapter(registration.getRegistrationId());
        if (adapter != null) return adapter.identity(registration, user, callback);
        String provider = providers.getOrDefault(registration.getRegistrationId(), registration.getRegistrationId());
        String subject = user instanceof OidcUser oidc ? oidc.getIssuer() + "|" + oidc.getSubject() : user.getName();
        return new OAuth2Identity(provider, subject, user.getAttribute("name"), user.getAttribute("picture"),
                user.getAttribute("email"), user.getAttributes());
    }
}
