package com.bingchunmoli.oauth.client;

import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;

/** Optional overrides return null to delegate to Spring Security. No Servlet types here. */
public interface OAuth2ProviderAdapter {
    String id();
    default Map<String, String> defaults() { return Map.of(); }
    default Set<AuthorizationGrantType> supportedGrantTypes() {
        return Set.of(AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.CLIENT_CREDENTIALS);
    }
    default void customizeAuthorization(OAuth2AuthorizationRequest.Builder request) { }
    /** Validate channel-specific invariants, including for host-supplied registrations. */
    default void validateRegistration(ClientRegistration registration) { }
    default OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> authorizationCodeClient() { return null; }
    default OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refreshTokenClient() { return null; }
    default OAuth2UserService<OAuth2UserRequest, OAuth2User> userService() { return null; }
    default OAuth2Identity identity(ClientRegistration registration, OAuth2User user) {
        String subject = user instanceof org.springframework.security.oauth2.core.oidc.user.OidcUser oidc
                ? oidc.getIssuer() + "|" + oidc.getSubject() : user.getName();
        return new OAuth2Identity(id(), subject, user.getAttribute("name"),
                user.getAttribute("picture"), user.getAttribute("email"), user.getAttributes());
    }
}
