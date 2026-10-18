package com.bingchunmoli.oauth.client;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import static org.assertj.core.api.Assertions.*;

class OAuth2CoreTest {
    @Test void storageExpirationNeverDiscardsAStillUsableAccessToken() {
        Instant now = Instant.now();
        var registration = registration();
        var refresh = new OAuth2RefreshToken("refresh", now.minusSeconds(100), now.plusSeconds(30));
        assertThat(TokenSnapshotCodec.effectiveExpiration(client(registration, now.plusSeconds(300), refresh)))
                .isEqualTo(now.plusSeconds(300));
        assertThat(TokenSnapshotCodec.effectiveExpiration(client(registration, null, refresh))).isNull();
        assertThat(TokenSnapshotCodec.effectiveExpiration(client(registration, now.plusSeconds(300),
                new OAuth2RefreshToken("refresh", now)))).isNull();
    }
    @Test void expiredRefreshDoesNotInvalidateAnAccessTokenWithinTheRefreshSkew() {
        var registration = registration();
        var registrations = new InMemoryClientRegistrationRepository(registration);
        var service = new InMemoryOAuth2AuthorizedClientService(registrations);
        var original = client(registration, Instant.now().plusSeconds(30),
                new OAuth2RefreshToken("expired-refresh", Instant.now().minusSeconds(100), Instant.now().minusSeconds(1)));
        service.saveAuthorizedClient(original, QuickOAuth2Client.principal("external-user"));
        AtomicInteger refreshAttempts = new AtomicInteger();
        var provider = OAuth2AuthorizedClientProviderBuilder.builder().refreshToken(refresh ->
                refresh.accessTokenResponseClient(request -> {
                    refreshAttempts.incrementAndGet();
                    throw new OAuth2AuthorizationException(new OAuth2Error("invalid_grant"));
                })).build();
        var coordinator = new LocalRefreshCoordinator(Duration.ofSeconds(1));
        var quick = new QuickOAuth2Client(new CoordinatedAuthorizedClientManager(registrations, service, provider, coordinator), service, coordinator);
        assertThat(quick.authorize(new AuthorizedClientRef("test", "external-user")).getAccessToken()).isSameAs(original.getAccessToken());
        assertThat(refreshAttempts).hasValue(0);
        assertThat(((OAuth2AuthorizedClient) service.loadAuthorizedClient("test", "external-user")).getAccessToken()).isSameAs(original.getAccessToken());
        service.saveAuthorizedClient(client(registration, Instant.now().minusSeconds(1), original.getRefreshToken()),
                QuickOAuth2Client.principal("external-user"));
        assertThatThrownBy(() -> quick.authorize(new AuthorizedClientRef("test", "external-user")))
                .isInstanceOf(AuthorizationRequiredException.class);
        assertThat((OAuth2AuthorizedClient) service.loadAuthorizedClient("test", "external-user")).isNull();
        assertThat(refreshAttempts).hasValue(0);
    }
    static ClientRegistration registration() {
        return ClientRegistration.withRegistrationId("test").clientId("client").clientSecret("do-not-persist")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://host/callback").authorizationUri("https://provider/authorize")
                .tokenUri("https://provider/token").userInfoUri("https://provider/user").userNameAttributeName("id").build();
    }
    static OAuth2AuthorizedClient client(ClientRegistration registration, Instant expires, OAuth2RefreshToken refresh) {
        return new OAuth2AuthorizedClient(registration, "external-user",
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-secret", Instant.now().minusSeconds(100), expires, Set.of("profile")), refresh);
    }
    @Test void snapshotsPreserveAllTokenTimesAndExcludeClientSecret() {
        var registration = registration();
        var refresh = new OAuth2RefreshToken("refresh-secret", Instant.now().minusSeconds(20), Instant.now().plusSeconds(1000));
        var original = client(registration, null, refresh);
        String encoded = TokenSnapshotCodec.encode(original);
        var restored = TokenSnapshotCodec.decode(encoded, new InMemoryClientRegistrationRepository(registration));
        assertThat(restored.getAccessToken().getExpiresAt()).isNull();
        assertThat(restored.getRefreshToken().getExpiresAt()).isEqualTo(refresh.getExpiresAt());
        assertThat(new String(Base64.getDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("do-not-persist");
        assertThat(TokenSnapshotCodec.effectiveExpiration(restored)).isNull();
        assertThat(TokenSnapshotCodec.key(new AuthorizedClientRef("a:b", "c")))
                .isNotEqualTo(TokenSnapshotCodec.key(new AuthorizedClientRef("a", "b:c")));
        assertThatThrownBy(() -> TokenSnapshotCodec.decode("broken", id -> registration)).hasMessage("Invalid OAuth2 authorization snapshot");
    }
    @Test void parallelCallersRefreshOnceAndReloadAfterLock() throws Exception {
        var registration = registration();
        var registrations = new InMemoryClientRegistrationRepository(registration);
        var service = new InMemoryOAuth2AuthorizedClientService(registrations);
        service.saveAuthorizedClient(client(registration, Instant.now().minusSeconds(1),
                new OAuth2RefreshToken("refresh", Instant.now().minusSeconds(50))), QuickOAuth2Client.principal("external-user"));
        AtomicInteger refreshed = new AtomicInteger();
        OAuth2AuthorizedClientProvider provider = context -> {
            if (context.getAuthorizedClient().getAccessToken().getExpiresAt().isAfter(Instant.now())) return null;
            refreshed.incrementAndGet();
            return client(registration, Instant.now().plusSeconds(1000), context.getAuthorizedClient().getRefreshToken());
        };
        var coordinator = new LocalRefreshCoordinator(Duration.ofSeconds(5));
        var manager = new CoordinatedAuthorizedClientManager(registrations, service, provider, coordinator);
        var quick = new QuickOAuth2Client(manager, service, coordinator);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<OAuth2AuthorizedClient>> futures = new ArrayList<>();
            for (int i = 0; i < 24; i++) futures.add(pool.submit(() -> quick.authorize(new AuthorizedClientRef("test", "external-user"))));
            for (var future : futures) assertThat(future.get(5, TimeUnit.SECONDS).getAccessToken().getExpiresAt()).isAfter(Instant.now());
        } finally { pool.shutdownNow(); }
        assertThat(refreshed).hasValue(1);
    }
    @Test void missingOrExpiredAuthorizationNeverEscapesAsAValidClient() {
        var registration = registration();
        var registrations = new InMemoryClientRegistrationRepository(registration);
        var service = new InMemoryOAuth2AuthorizedClientService(registrations);
        var coordinator = new LocalRefreshCoordinator(Duration.ofSeconds(1));
        var quick = new QuickOAuth2Client(new CoordinatedAuthorizedClientManager(registrations, service, c -> null, coordinator), service, coordinator);
        assertThatThrownBy(() -> quick.authorize(new AuthorizedClientRef("test", "external-user"))).isInstanceOf(AuthorizationRequiredException.class);
        service.saveAuthorizedClient(client(registration, Instant.now().minusSeconds(1), null), QuickOAuth2Client.principal("external-user"));
        assertThatThrownBy(() -> quick.authorize(new AuthorizedClientRef("test", "external-user"))).isInstanceOf(AuthorizationRequiredException.class);
        assertThat(quick.application("test").principalName()).isEqualTo("quick-oauth2:application:test");
    }
    @Test void invalidGrantRemovesAuthorizationAndSanitizesProviderError() {
        var registration = registration();
        var registrations = new InMemoryClientRegistrationRepository(registration);
        var service = new InMemoryOAuth2AuthorizedClientService(registrations);
        service.saveAuthorizedClient(client(registration, Instant.now().minusSeconds(1), null), QuickOAuth2Client.principal("external-user"));
        var coordinator = new LocalRefreshCoordinator(Duration.ofSeconds(1));
        var manager = new CoordinatedAuthorizedClientManager(registrations, service, context -> {
            throw new ClientAuthorizationException(new OAuth2Error("invalid_grant", "refresh-secret", null), "test");
        }, coordinator);
        var quick = new QuickOAuth2Client(manager, service, coordinator);
        assertThatThrownBy(() -> quick.authorize(new AuthorizedClientRef("test", "external-user")))
                .hasMessage("OAuth2 authorization is required").hasNoCause();
        assertThat((OAuth2AuthorizedClient) service.loadAuthorizedClient("test", "external-user")).isNull();
    }
    @Test void invalidGrantFromACustomProviderAlsoRemovesTheFailedAuthorization() {
        var registration = registration();
        var registrations = new InMemoryClientRegistrationRepository(registration);
        var service = new InMemoryOAuth2AuthorizedClientService(registrations);
        var expired = client(registration, Instant.now().minusSeconds(1), new OAuth2RefreshToken("refresh", Instant.now().minusSeconds(100)));
        service.saveAuthorizedClient(expired, QuickOAuth2Client.principal("external-user"));
        service.saveAuthorizedClient(new OAuth2AuthorizedClient(registration, "another-user", expired.getAccessToken()),
                QuickOAuth2Client.principal("another-user"));
        var coordinator = new LocalRefreshCoordinator(Duration.ofSeconds(1));
        var manager = new CoordinatedAuthorizedClientManager(registrations, service, context -> {
            throw new OAuth2AuthorizationException(new OAuth2Error("invalid_grant", "sensitive-provider-description", null));
        }, coordinator);
        var quick = new QuickOAuth2Client(manager, service, coordinator);
        assertThatThrownBy(() -> quick.authorize(new AuthorizedClientRef("test", "external-user")))
                .hasMessage("OAuth2 authorization is required").hasNoCause();
        assertThat((OAuth2AuthorizedClient) service.loadAuthorizedClient("test", "external-user")).isNull();
        assertThat((OAuth2AuthorizedClient) service.loadAuthorizedClient("test", "another-user")).isNotNull();
    }
    @Test void tokenResponseMetadataIsAppliedOnce() {
        var registration = registration();
        var response = OAuth2AccessTokenResponse.withToken("access").tokenType(OAuth2AccessToken.TokenType.BEARER)
                .refreshToken("refresh").expiresIn(300).additionalParameters(Map.of("refresh_token_expires_in", 800, "quick_non_expiring_access", true)).build();
        TokenResponseMetadata.capture("test", response);
        var original = new OAuth2AuthorizedClient(registration, "external-user", response.getAccessToken(), response.getRefreshToken());
        var normalized = TokenResponseMetadata.applyAndClear(original);
        assertThat(normalized.getAccessToken().getExpiresAt()).isNull();
        assertThat(normalized.getRefreshToken().getExpiresAt()).isEqualTo(response.getRefreshToken().getIssuedAt().plusSeconds(800));
        assertThat(TokenResponseMetadata.applyAndClear(original)).isSameAs(original);
    }
    @Test void anErrorForAnotherRegistrationCannotDeleteItsStoredAuthorization() {
        var requested = registration();
        var other = ClientRegistration.withRegistrationId("other").clientId("client")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).tokenUri("https://provider/token").build();
        var registrations = new InMemoryClientRegistrationRepository(requested, other);
        var service = new InMemoryOAuth2AuthorizedClientService(registrations);
        service.saveAuthorizedClient(client(requested, Instant.now().minusSeconds(1), null), QuickOAuth2Client.principal("external-user"));
        service.saveAuthorizedClient(client(other, Instant.now().plusSeconds(100), null), QuickOAuth2Client.principal("external-user"));
        var coordinator = new LocalRefreshCoordinator(Duration.ofSeconds(1));
        var manager = new CoordinatedAuthorizedClientManager(registrations, service, context -> {
            throw new ClientAuthorizationException(new OAuth2Error("invalid_grant"), "other");
        }, coordinator);
        var quick = new QuickOAuth2Client(manager, service, coordinator);
        assertThatThrownBy(() -> quick.authorize(new AuthorizedClientRef("test", "external-user")))
                .isInstanceOf(AuthorizationRequiredException.class);
        assertThat((OAuth2AuthorizedClient) service.loadAuthorizedClient("test", "external-user")).isNotNull();
        assertThat((OAuth2AuthorizedClient) service.loadAuthorizedClient("other", "external-user")).isNotNull();
    }
    @Test void duplicateChannelIdsFailInsteadOfDependingOnDiscoveryOrder() {
        OAuth2ProviderAdapter adapter = () -> "duplicate";
        assertThatThrownBy(() -> new OAuth2ProviderRegistry(List.of(adapter, adapter), Map.of())).hasMessageContaining("Duplicate");
    }
    @Test void nestedApiAuthorizationDoesNotEraseBrowserTokenMetadata() {
        var browser = OAuth2AccessTokenResponse.withToken("browser-token").tokenType(OAuth2AccessToken.TokenType.BEARER)
                .expiresIn(300).additionalParameters(Map.of("quick_non_expiring_access", true)).build();
        TokenResponseMetadata.capture("test", browser);
        try (var scope = TokenResponseMetadata.scope()) {
            var api = OAuth2AccessTokenResponse.withToken("api-token").tokenType(OAuth2AccessToken.TokenType.BEARER).expiresIn(300).build();
            TokenResponseMetadata.capture("test", api);
            assertThat(TokenResponseMetadata.applyAndClear(new OAuth2AuthorizedClient(registration(), "api",
                    api.getAccessToken())).getAccessToken().getExpiresAt()).isNotNull();
        }
        assertThat(TokenResponseMetadata.applyAndClear(new OAuth2AuthorizedClient(registration(), "browser",
                browser.getAccessToken())).getAccessToken().getExpiresAt()).isNull();
    }
    @Test void unchangedRefreshTokenKeepsItsPreviouslyKnownExpiration() {
        var registration = registration();
        var registrations = new InMemoryClientRegistrationRepository(registration);
        var service = new InMemoryOAuth2AuthorizedClientService(registrations);
        Instant refreshExpiry = Instant.now().plusSeconds(3600);
        service.saveAuthorizedClient(client(registration, Instant.now().minusSeconds(1),
                new OAuth2RefreshToken("same-refresh", Instant.now().minusSeconds(100), refreshExpiry)),
                QuickOAuth2Client.principal("external-user"));
        var coordinator = new LocalRefreshCoordinator(Duration.ofSeconds(1));
        var manager = new CoordinatedAuthorizedClientManager(registrations, service,
                context -> client(registration, Instant.now().plusSeconds(300),
                        new OAuth2RefreshToken("same-refresh", Instant.now())), coordinator);
        var refreshed = manager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId("test")
                .principal(QuickOAuth2Client.principal("external-user")).build());
        assertThat(refreshed.getRefreshToken().getExpiresAt()).isEqualTo(refreshExpiry);
    }
    @Test void oidcAdapterIdentityUsesIssuerAndSubjectEvenWhenPrincipalNameIsCustomized() {
        var token = new org.springframework.security.oauth2.core.oidc.OidcIdToken("id-token",
                Instant.now(), Instant.now().plusSeconds(300),
                Map.of("iss", "https://issuer.example", "sub", "stable", "preferred_username", "mutable"));
        var user = new org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser(
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("USER")), token, "preferred_username");
        OAuth2ProviderAdapter adapter = () -> "oidc-provider";
        assertThat(user.getName()).isEqualTo("mutable");
        assertThat(adapter.identity(registration(), user).subject()).isEqualTo("https://issuer.example|stable");
    }
}
