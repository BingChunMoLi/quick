package com.bingchunmoli.oauth.server.authorization;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.*;
import static org.assertj.core.api.Assertions.*;
class AuthorizationCoreTest {
    RegisteredClient client() {return RegisteredClient.withId("id").clientId("client").clientSecret("{bcrypt}hash")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://127.0.0.1/callback").scope("read").tokenSettings(TokenSettings.builder().reuseRefreshTokens(false).build()).build();}
    @Test void snapshotsRoundTripNativeSettingsPrincipalMetadataAndConsent() {
        var codec=new AuthorizationSnapshotCodec(new SecurityAuthorizationAttributeCodec());var client=client();
        var decoded=codec.client(codec.client(client));assertThat(decoded.getTokenSettings().isReuseRefreshTokens()).isFalse();
        var principal=new User("alice","temporary-user-password",List.of());principal.eraseCredentials();
        var authorization=OAuth2Authorization.withRegisteredClient(client).id("authorization").principalName("alice")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).authorizedScopes(Set.of("read"))
                .attribute(java.security.Principal.class.getName(),UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of()))
                .token(new OAuth2AuthorizationCode("authorization-code",Instant.now(),Instant.now().plusSeconds(60)))
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"access-token",Instant.now(),Instant.now().plusSeconds(90)),
                        metadata->metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME,Map.of("sub","alice")))
                .refreshToken(new OAuth2RefreshToken("refresh-token",Instant.now(),Instant.now().plusSeconds(120))).build();
        String data=codec.authorization(authorization);assertThat(data).doesNotContain("temporary-user-password","{bcrypt}hash");
        var restored=codec.authorization(data,new InMemoryRegisteredClientRepository(client));
        assertThat(restored.getAccessToken().getClaims()).containsEntry("sub","alice");
        assertThat(restored.getRefreshToken().getToken().getExpiresAt()).isEqualTo(authorization.getRefreshToken().getToken().getExpiresAt());
        assertThat((Object)restored.getAttribute(java.security.Principal.class.getName())).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        var consent=OAuth2AuthorizationConsent.withId("id","alice").scope("read").build();
        assertThat(codec.consent(codec.consent(consent)).getScopes()).containsExactly("read");
    }
    @Test void credentialsAndUnknownVersionsAreRejectedWithoutLeakingData() {
        var codec=new SecurityAuthorizationAttributeCodec();
        assertThatThrownBy(()->codec.encode(Map.of("principal",UsernamePasswordAuthenticationToken.authenticated("alice","private-password",List.of()))))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("private-password");
        var snapshots=new AuthorizationSnapshotCodec(codec);
        assertThatThrownBy(()->snapshots.client("{\"version\":2}")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->codec.decode("{\"@class\":\"java.lang.Runtime\"}")).isInstanceOf(IllegalStateException.class);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"map","list","details","user"})
    void nestedCredentialsAreRejectedBeforeSerialization(String location) {
        var authentication=UsernamePasswordAuthenticationToken.authenticated("alice","private-nested-password",List.of());
        Object value=switch(location) {
            case "map" -> Map.of("delegated",authentication);
            case "list" -> List.of(authentication);
            case "details" -> {
                var outer=UsernamePasswordAuthenticationToken.authenticated("alice",null,List.of());
                outer.setDetails(Map.of("delegated",authentication));yield outer;
            }
            default -> new User("alice","private-nested-password",List.of());
        };
        assertThatThrownBy(()->new SecurityAuthorizationAttributeCodec().encode(Map.of("context",value)))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("private-nested-password");
    }
    @Test void cyclicAttributesFailClearlyAndSharedErasedPrincipalsRemainSupported() {
        var codec=new SecurityAuthorizationAttributeCodec();var cycle=new LinkedHashMap<String,Object>();cycle.put("self",cycle);
        assertThatThrownBy(()->codec.encode(cycle)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Cyclic");
        var erased=UsernamePasswordAuthenticationToken.authenticated("alice",null,List.of());
        var values=new LinkedHashMap<String,Object>(Map.of("first",List.of(erased),"second",List.of(erased)));
        assertThat(codec.decode(codec.encode(values))).containsKeys("first","second");
    }
    @Test void readingLegacyNestedCredentialsFailsWithoutExposingStoredValues() {
        var nativeJson=tools.jackson.databind.json.JsonMapper.builder().addModules(org.springframework.security.jackson.SecurityJacksonModules
                .getModules(getClass().getClassLoader())).build();
        var authentication=UsernamePasswordAuthenticationToken.authenticated("alice","private-restored-password",List.of());
        String document=nativeJson.writeValueAsString(new LinkedHashMap<>(Map.of("context",new ArrayList<>(List.of(authentication)))));
        assertThat(document).contains("private-restored-password");
        assertThatThrownBy(()->new SecurityAuthorizationAttributeCodec().decode(document))
                .isInstanceOf(IllegalStateException.class).hasMessage("Invalid authorization attributes").hasNoCause();
    }
    @Test void waitingGrantTimesOutAndDifferentAuthorizationsProceedIndependently() throws Exception {
        var coordinator=new LocalGrantCoordinator(Duration.ofMillis(80));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        try {
            Future<Integer> first=pool.submit(()->coordinator.execute("one",()->{entered.countDown();try{release.await();}catch(InterruptedException e){throw new IllegalStateException(e);}return 1;}));
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->coordinator.execute("one",()->2)).isInstanceOf(OAuth2AuthenticationException.class);
            assertThat(coordinator.execute("two",()->2)).isEqualTo(2);
            release.countDown();assertThat(first.get(2,TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(coordinator.execute("one",()->3)).isEqualTo(3);
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test void expiredMemoryAuthorizationIsRemovedButUnknownRefreshLifetimeIsRetained() {
        try(var service=new ExpiringMemoryAuthorizationService()) {
            var expired=OAuth2Authorization.withRegisteredClient(client()).id("expired").principalName("alice")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"expired",Instant.now().minusSeconds(90),Instant.now().minusSeconds(60))).build();
            service.save(expired);service.cleanupExpired();assertThat(service.findById("expired")).isNull();
            var unknown=OAuth2Authorization.from(expired).id("unknown").refreshToken(new OAuth2RefreshToken("refresh",Instant.now())).build();
            service.save(unknown);service.cleanupExpired();assertThat(service.findById("unknown")).isNotNull();
        }
    }
}
