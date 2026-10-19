package com.bingchunmoli.oauth.server.authorization;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.authentication.*;
/** Decorates native providers; OAuth validation and token issuance remain native. */
public final class CoordinatingGrantProvider implements AuthenticationProvider {
    private final AuthenticationProvider delegate;
    private final OAuth2AuthorizationService service;
    private final GrantCoordinator coordinator;
    public CoordinatingGrantProvider(AuthenticationProvider delegate, OAuth2AuthorizationService service, GrantCoordinator coordinator) {
        this.delegate = delegate; this.service = service; this.coordinator = coordinator;
    }
    @Override public Authentication authenticate(Authentication authentication) {
        String value;
        OAuth2TokenType type;
        if (authentication instanceof OAuth2AuthorizationCodeAuthenticationToken code) {
            value = code.getCode(); type = new OAuth2TokenType("code");
        } else if (authentication instanceof OAuth2RefreshTokenAuthenticationToken refresh) {
            value = refresh.getRefreshToken(); type = OAuth2TokenType.REFRESH_TOKEN;
        } else if (authentication instanceof OAuth2TokenRevocationAuthenticationToken revoke) {
            value = revoke.getToken(); type = null;
        } else if (authentication instanceof OAuth2AuthorizationConsentAuthenticationToken consent &&
                !(authentication instanceof OAuth2DeviceAuthorizationConsentAuthenticationToken)) {
            value = consent.getState(); type = new OAuth2TokenType("state");
        } else return delegate.authenticate(authentication);
        OAuth2Authorization initial = service.findByToken(value, type);
        if (initial == null) return delegate.authenticate(authentication);
        try {
            return coordinator.execute(initial.getId(), () -> {
                coordinator.checkOwned();
                Authentication result = delegate.authenticate(authentication);
                coordinator.checkOwned();
                return result;
            });
        } catch(OAuth2AuthenticationException failure) {
            if(authentication instanceof OAuth2AuthorizationConsentAuthenticationToken &&
                    !(failure instanceof OAuth2AuthorizationCodeRequestAuthenticationException))
                // The native authorization endpoint failure handler requires this subtype; no unverified redirect.
                throw new OAuth2AuthorizationCodeRequestAuthenticationException(failure.getError(),null);
            throw failure;
        }
    }
    @Override public boolean supports(Class<?> type) { return delegate.supports(type); }
}
