package com.bingchunmoli.oauth.server.authorization.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

/** Validates UserInfo bearer tokens against the issuing server's own authorization records. */
final class AuthorizationServiceTokenIntrospector implements OpaqueTokenIntrospector {
    private final OAuth2AuthorizationService authorizations;

    AuthorizationServiceTokenIntrospector(OAuth2AuthorizationService authorizations) {
        this.authorizations=authorizations;
    }

    @Override public OAuth2AuthenticatedPrincipal introspect(String value) {
        try {
            var authorization=authorizations.findByToken(value,OAuth2TokenType.ACCESS_TOKEN);
            var saved=authorization==null?null:authorization.getAccessToken();
            if(saved==null || !value.equals(saved.getToken().getTokenValue()) || !saved.isActive() ||
                    !OAuth2AccessToken.TokenType.BEARER.equals(saved.getToken().getTokenType()))
                throw new BadOpaqueTokenException("UserInfo access token verification failed");
            var token=saved.getToken();
            Map<String,Object> claims=new LinkedHashMap<>();
            if(saved.getClaims()!=null) claims.putAll(saved.getClaims());
            claims.putIfAbsent("sub",authorization.getPrincipalName());
            claims.put("active",true);
            claims.put("token_type",token.getTokenType().getValue());
            claims.put("iat",token.getIssuedAt());
            claims.put("exp",token.getExpiresAt());
            claims.put("scope",List.copyOf(token.getScopes()));
            var authorities=token.getScopes().stream().<GrantedAuthority>map(scope->new SimpleGrantedAuthority("SCOPE_"+scope)).toList();
            return new DefaultOAuth2AuthenticatedPrincipal(authorization.getPrincipalName(),claims,authorities);
        } catch(RuntimeException unavailable) {
            // Store/codec errors must fail closed without exposing token values or persisted attributes.
            throw new BadOpaqueTokenException("UserInfo access token verification failed");
        }
    }
}
