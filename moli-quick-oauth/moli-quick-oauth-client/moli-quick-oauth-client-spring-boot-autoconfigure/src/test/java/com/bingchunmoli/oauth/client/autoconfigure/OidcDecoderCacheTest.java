package com.bingchunmoli.oauth.client.autoconfigure;

import com.bingchunmoli.oauth.client.OAuth2ProviderRegistry;
import com.bingchunmoli.oauth.client.oidc.OidcProviderAdapter;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;

class OidcDecoderCacheTest {
    private final AtomicInteger creations = new AtomicInteger();
    private final JwtDecoderFactory<ClientRegistration> factory = new QuickOAuth2WebAutoConfiguration()
            .quickOidcIdTokenDecoderFactory(new OAuth2ProviderRegistry(List.of(new OidcProviderAdapter() {
                @Override public String id() { return "test"; }
                @Override public JwtDecoder idTokenDecoder(ClientRegistration registration) {
                    creations.incrementAndGet();
                    return token -> Jwt.withTokenValue(token).header("alg", "RS256").subject("user")
                            .issuer(registration.getProviderDetails().getIssuerUri()).audience(List.of(registration.getClientId()))
                            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
                }
            }), Map.of("first", "test", "second", "test")));

    private static ClientRegistration registration(String id) {
        return ClientRegistration.withRegistrationId(id).clientId("app").clientSecret("secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).scope("openid", "email")
                .redirectUri("https://app.test/callback").authorizationUri("https://issuer.test/authorize")
                .tokenUri("https://issuer.test/token").issuerUri("https://issuer.test").jwkSetUri("https://issuer.test/keys").build();
    }

    @Test void equalRegistrationCopiesReuseOneDecoder() {
        var original = registration("first"); var decoder = factory.createDecoder(original);
        for (int i = 0; i < 100; i++)
            assertThat(factory.createDecoder(ClientRegistration.withClientRegistration(original).build())).isSameAs(decoder);
        assertThat(creations.get()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"client-id", "secret", "issuer", "jwks", "metadata", "scopes"})
    void changedConfigurationReplacesTheDecoder(String change) {
        var original = registration("first"); var previous = factory.createDecoder(original);
        var updated = ClientRegistration.withClientRegistration(original);
        switch (change) {
            case "client-id" -> updated.clientId("new-app");
            case "secret" -> updated.clientSecret("new-secret");
            case "issuer" -> updated.issuerUri("https://new-issuer.test");
            case "jwks" -> updated.jwkSetUri("https://new-issuer.test/keys");
            case "metadata" -> updated.providerConfigurationMetadata(Map.of("id_token_signing_alg_values_supported", List.of("ES256")));
            default -> updated.scope("openid", "profile");
        }
        var registration = updated.build(); var replacement = factory.createDecoder(registration);
        assertThat(replacement).isNotSameAs(previous);
        assertThat(factory.createDecoder(ClientRegistration.withClientRegistration(registration).build())).isSameAs(replacement);
        assertThat(replacement.decode("test").getAudience()).containsExactly(registration.getClientId());
        assertThat(replacement.decode("test").getIssuer().toString()).isEqualTo(registration.getProviderDetails().getIssuerUri());
        assertThat(creations.get()).isEqualTo(2);
        assertThat((Map<?, ?>) ReflectionTestUtils.getField(factory, "entries")).hasSize(1);
    }

    @Test void nestedMetadataChangesAreDetectedEvenForTheSameRegistrationInstance() {
        var algorithms = new ArrayList<>(List.of("RS256"));
        var registration = ClientRegistration.withClientRegistration(registration("first"))
                .providerConfigurationMetadata(Map.of("nested", Map.of("algorithms", algorithms))).build();
        var previous = factory.createDecoder(registration);
        algorithms.add("ES256");
        assertThat(factory.createDecoder(registration)).isNotSameAs(previous);
        assertThat(creations.get()).isEqualTo(2);
    }

    @Test void equivalentScopeAndMetadataOrderingDoesNotRebuild() {
        var original = ClientRegistration.withClientRegistration(registration("first"))
                .providerConfigurationMetadata(Map.of("issuer", "https://issuer.test", "algorithms", List.of("RS256"))).build();
        var reordered = new LinkedHashMap<String, Object>();
        reordered.put("algorithms", List.of("RS256")); reordered.put("issuer", "https://issuer.test");
        var copy = ClientRegistration.withClientRegistration(original).scope("email", "openid")
                .providerConfigurationMetadata(reordered).build();
        assertThat(factory.createDecoder(copy)).isSameAs(factory.createDecoder(original));
        assertThat(creations.get()).isEqualTo(1);
    }

    @Test void concurrentEquivalentLookupsCreateOnlyOneDecoder() throws Exception {
        var workers = Executors.newFixedThreadPool(8); var start = new CountDownLatch(1);
        try {
            var results = new ArrayList<Future<JwtDecoder>>();
            for (int i = 0; i < 32; i++) results.add(workers.submit(() -> {
                start.await(); return factory.createDecoder(registration("first"));
            }));
            start.countDown(); var expected = results.get(0).get(3, TimeUnit.SECONDS);
            for (var result : results) assertThat(result.get(3, TimeUnit.SECONDS)).isSameAs(expected);
            assertThat(creations.get()).isEqualTo(1);
        } finally { workers.shutdownNow(); }
    }

    @Test void distinctRegistrationIdsHaveSeparateEntries() {
        assertThat(factory.createDecoder(registration("first"))).isNotSameAs(factory.createDecoder(registration("second")));
        assertThat(creations.get()).isEqualTo(2);
    }

    @Test void leastRecentlyUsedRegistrationIsEvictedAtCapacity() {
        var nativeFactory = new QuickOAuth2WebAutoConfiguration().quickOidcIdTokenDecoderFactory(new OAuth2ProviderRegistry(List.of(), Map.of()));
        var first = registration("registration-0"); var previous = nativeFactory.createDecoder(first);
        for (int i = 1; i <= 256; i++) nativeFactory.createDecoder(registration("registration-" + i));
        assertThat((Map<?, ?>) ReflectionTestUtils.getField(nativeFactory, "entries")).hasSize(256);
        assertThat(nativeFactory.createDecoder(first)).isNotSameAs(previous);
        assertThat((Map<?, ?>) ReflectionTestUtils.getField(nativeFactory, "entries")).hasSize(256);
    }
}
