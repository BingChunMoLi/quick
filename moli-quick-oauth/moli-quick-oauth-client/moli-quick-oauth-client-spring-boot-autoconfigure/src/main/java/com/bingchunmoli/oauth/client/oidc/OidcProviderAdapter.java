package com.bingchunmoli.oauth.client.oidc;

import com.bingchunmoli.oauth.client.OAuth2ProviderAdapter;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Optional OIDC overrides; null retains Spring's standard decoder and user service. */
public interface OidcProviderAdapter extends OAuth2ProviderAdapter {
    default JwtDecoder idTokenDecoder(ClientRegistration registration) { return null; }
    default OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() { return null; }
}
