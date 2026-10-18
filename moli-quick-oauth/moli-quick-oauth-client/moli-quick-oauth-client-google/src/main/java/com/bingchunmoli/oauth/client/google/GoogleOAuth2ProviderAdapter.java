package com.bingchunmoli.oauth.client.google;

import com.bingchunmoli.oauth.client.*;
import java.util.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

/** Google server-side OIDC. Signature, issuer, audience and nonce checks remain native. */
public final class GoogleOAuth2ProviderAdapter implements OAuth2ProviderAdapter {
    private final GoogleOAuth2Properties properties;
    public GoogleOAuth2ProviderAdapter(GoogleOAuth2Properties properties) { this.properties = properties; }
    @Override public String id() { return "google"; }
    @Override public Map<String, String> defaults() {
        return Map.of("authorization-uri", "https://accounts.google.com/o/oauth2/v2/auth",
                "token-uri", "https://oauth2.googleapis.com/token", "user-info-uri", "https://openidconnect.googleapis.com/v1/userinfo",
                "user-name-attribute", "sub", "jwk-set-uri", "https://www.googleapis.com/oauth2/v3/certs",
                "issuer-uri", "https://accounts.google.com", "authorization-grant-type", "authorization_code",
                "client-authentication-method", "client_secret_post", "redirect-uri", "{baseUrl}/login/oauth2/code/{registrationId}",
                "scope", "openid,profile,email");
    }
    @Override public Set<AuthorizationGrantType> supportedGrantTypes() { return Set.of(AuthorizationGrantType.AUTHORIZATION_CODE); }
    @Override public void validateRegistration(ClientRegistration registration) {
        if (!registration.getScopes().contains("openid") || registration.getProviderDetails().getIssuerUri() == null ||
                registration.getProviderDetails().getJwkSetUri() == null) {
            throw new IllegalStateException("Google login requires openid scope, issuer and JWK configuration: " + registration.getRegistrationId());
        }
    }
    @Override public void customizeAuthorization(OAuth2AuthorizationRequest.Builder request) {
        OAuth2AuthorizationRequestCustomizers.withPkce().accept(request);
        request.additionalParameters(parameters -> {
            if (properties.isOffline()) parameters.put("access_type", "offline");
            if (properties.getPrompt() != null && !properties.getPrompt().isBlank()) parameters.put("prompt", properties.getPrompt());
        });
    }
    @Override public OAuth2Identity identity(ClientRegistration registration, OAuth2User user) {
        if (!(user instanceof OidcUser oidc)) throw new IllegalStateException("Google login requires a validated OIDC user");
        return new OAuth2Identity(id(), oidc.getIssuer() + "|" + oidc.getSubject(), oidc.getFullName(), oidc.getPicture(),
                Boolean.TRUE.equals(oidc.getEmailVerified()) ? oidc.getEmail() : null, oidc.getAttributes());
    }
}
