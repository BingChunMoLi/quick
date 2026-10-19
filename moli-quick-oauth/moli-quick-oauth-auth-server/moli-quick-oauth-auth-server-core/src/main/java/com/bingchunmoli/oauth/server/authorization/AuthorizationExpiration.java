package com.bingchunmoli.oauth.server.authorization;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.*;
public final class AuthorizationExpiration {
    private AuthorizationExpiration() { }
    /** Keep unknown token lifetimes; pending consent has a ten-minute lifetime. */
    public static Instant latest(OAuth2Authorization authorization) {
        Instant result=null;
        for(Class<? extends OAuth2Token> type:List.of(OAuth2AuthorizationCode.class,OAuth2AccessToken.class,OAuth2RefreshToken.class,OidcIdToken.class)) {
            var token=authorization.getToken(type);if(token==null)continue;
            Instant expiry=token.getToken().getExpiresAt();if(expiry==null)return null;
            if(result==null || expiry.isAfter(result))result=expiry;
        }
        return result==null?Instant.now().plusSeconds(600):result;
    }
}
