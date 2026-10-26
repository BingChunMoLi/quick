package com.bingchunmoli.oauth.server.authorization.web;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthorizationServiceTokenIntrospectorTest {
    final OAuth2AuthorizationService service=mock(OAuth2AuthorizationService.class);
    final AuthorizationServiceTokenIntrospector introspector=new AuthorizationServiceTokenIntrospector(service);
    final RegisteredClient client=RegisteredClient.withId("client").clientId("client")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://127.0.0.1/callback").scope("openid").build();

    OAuth2Authorization authorization(OAuth2AccessToken token) {
        return OAuth2Authorization.withRegisteredClient(client).principalName("alice")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).accessToken(token).build();
    }
    OAuth2AccessToken access(String value) {
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,value,Instant.now().minusSeconds(1),Instant.now().plusSeconds(300),Set.of("openid","profile"));
    }

    @ParameterizedTest @ValueSource(strings={"opaque-access-token","header.payload.signature"})
    void authenticatesExactStoredAccessTokenRegardlessOfFormat(String value) {
        var token=access(value);
        var authorization=OAuth2Authorization.from(authorization(token)).token(token,metadata->metadata.put(
                OAuth2Authorization.Token.CLAIMS_METADATA_NAME,Map.of("aud",List.of("api"),"scope",List.of("untrusted-extra-scope")))).build();
        when(service.findByToken(value,OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authorization);
        var principal=introspector.introspect(value);
        assertThat(principal.getName()).isEqualTo("alice");
        assertThat(principal.getAttributes()).containsEntry("sub","alice").containsEntry("active",true)
                .containsEntry("iat",token.getIssuedAt()).containsEntry("exp",token.getExpiresAt()).containsEntry("aud",List.of("api"));
        assertThat(principal.getAuthorities()).extracting(authority->authority.getAuthority()).containsExactlyInAnyOrder("SCOPE_openid","SCOPE_profile");
        assertThat(principal.<List<String>>getAttribute("scope")).containsExactlyInAnyOrder("openid","profile");
    }

    @Test void permitsUnknownTokenTimesWithoutRequiringJwtClaims() {
        when(service.findByToken("access",OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authorization(
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"access",null,null,Set.of("openid"))));
        var principal=introspector.introspect("access");
        assertThat(principal.getName()).isEqualTo("alice");
        assertThat(principal.getAttributes().get("exp")).isNull();
    }

    @ParameterizedTest @ValueSource(strings={"missing","different-value","no-access-token","expired","revoked","future","dpop"})
    void rejectsMissingInactiveAndWrongKindOfTokens(String state) {
        var token=access(state.equals("different-value")?"other-access-token":"private-access-token");
        var authorization=authorization(token);
        if(state.equals("missing"))authorization=null;
        else if(state.equals("no-access-token"))authorization=OAuth2Authorization.withRegisteredClient(client).principalName("alice")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).refreshToken(new OAuth2RefreshToken("private-access-token",Instant.now())).build();
        else if(state.equals("expired"))authorization=authorization(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"private-access-token",
                Instant.now().minusSeconds(120),Instant.now().minusSeconds(60)));
        else if(state.equals("revoked"))authorization=OAuth2Authorization.from(authorization).invalidate(token).build();
        else if(state.equals("future"))authorization=OAuth2Authorization.from(authorization).token(token,metadata->metadata.put(
                OAuth2Authorization.Token.CLAIMS_METADATA_NAME,Map.of("nbf",Instant.now().plusSeconds(300)))).build();
        else if(state.equals("dpop"))authorization=authorization(new OAuth2AccessToken(OAuth2AccessToken.TokenType.DPOP,"private-access-token",
                Instant.now(),Instant.now().plusSeconds(300)));
        when(service.findByToken("private-access-token",OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authorization);
        assertThatThrownBy(()->introspector.introspect("private-access-token")).isInstanceOf(BadOpaqueTokenException.class)
                .hasMessage("UserInfo access token verification failed").hasMessageNotContaining("private-access-token");
    }

    @Test void storeFailureIsRejectedWithoutExposingStoredData() {
        when(service.findByToken("private-access-token",OAuth2TokenType.ACCESS_TOKEN)).thenThrow(new IllegalStateException("private-database-value"));
        assertThatThrownBy(()->introspector.introspect("private-access-token")).isInstanceOf(BadOpaqueTokenException.class)
                .hasMessage("UserInfo access token verification failed").hasNoCause();
    }
}
