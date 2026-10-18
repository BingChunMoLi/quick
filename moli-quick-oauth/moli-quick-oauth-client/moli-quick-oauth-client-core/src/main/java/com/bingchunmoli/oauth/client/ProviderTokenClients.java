package com.bingchunmoli.oauth.client;

import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

/** Provider selection is based on registration, never inferred from endpoint URLs. */
public final class ProviderTokenClients {
    private final OAuth2ProviderRegistry registry;
    private final OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> code;
    private final OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refresh;
    private final OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> defaultCode = new RestClientAuthorizationCodeTokenResponseClient();
    private final OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> defaultRefresh = new RestClientRefreshTokenTokenResponseClient();
    public ProviderTokenClients(OAuth2ProviderRegistry registry) {
        this(registry, null, null);
    }
    /** Explicit clients override channel defaults independently; null delegates to the channel or Spring. */
    public ProviderTokenClients(OAuth2ProviderRegistry registry,
            OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> code,
            OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refresh) {
        this.registry = registry; this.code = code; this.refresh = refresh;
    }
    public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> authorizationCode() {
        return request -> {
            var adapter = registry.adapter(request.getClientRegistration().getRegistrationId());
            var client = code != null ? code : adapter == null ? null : adapter.authorizationCodeClient();
            return normalize(request.getClientRegistration().getRegistrationId(), (client == null ? defaultCode : client).getTokenResponse(request));
        };
    }
    public OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refreshToken() {
        return request -> {
            var adapter = registry.adapter(request.getClientRegistration().getRegistrationId());
            var client = refresh != null ? refresh : adapter == null ? null : adapter.refreshTokenClient();
            return normalize(request.getClientRegistration().getRegistrationId(), (client == null ? defaultRefresh : client).getTokenResponse(request));
        };
    }
    private OAuth2AccessTokenResponse normalize(String registrationId, OAuth2AccessTokenResponse response) {
        TokenResponseMetadata.capture(registrationId, response);
        return response;
    }
}
