package com.bingchunmoli.oauth.client.microsoft;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class MicrosoftJwkTimeoutTest {
    private static final String TENANT = "11111111-2222-3333-4444-555555555555";
    private static final String ISSUER = MicrosoftOAuth2ProviderAdapter.AUTHORITY + TENANT + "/v2.0";
    private static RSAKey key;
    private static byte[] keys;
    private static String token;

    @BeforeAll static void keys() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("test-key").generate();
        var publicKey = new LinkedHashMap<>(key.toPublicJWK().toJSONObject());
        publicKey.put("issuer", MicrosoftOAuth2ProviderAdapter.AUTHORITY + "{tenantid}/v2.0");
        keys = JsonMapper.builder().build().writeValueAsBytes(Map.of("keys", List.of(publicKey)));
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                new JWTClaimsSet.Builder().issuer(ISSUER).subject("user").audience("app")
                        .issueTime(Date.from(Instant.now().minusSeconds(60)))
                        .expirationTime(Date.from(Instant.now().plusSeconds(300))).claim("tid", TENANT).build());
        jwt.sign(new RSASSASigner(key)); token = jwt.serialize();
    }

    private static ClientRegistration registration(String base) {
        return ClientRegistration.withRegistrationId("microsoft").clientId("app").clientSecret("secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).scope("openid")
                .redirectUri("https://app.test/callback").authorizationUri(base + "/authorize").tokenUri(base + "/token")
                .issuerUri(MicrosoftOAuth2ProviderAdapter.AUTHORITY + "{tenantid}/v2.0").jwkSetUri(base + "/keys").build();
    }

    private static final class Endpoint implements AutoCloseable {
        final HttpServer server;
        final ExecutorService workers = Executors.newCachedThreadPool();
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final AtomicBoolean stall = new AtomicBoolean(true);
        final AtomicInteger calls = new AtomicInteger();
        Endpoint(boolean sendHeaders) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(workers);
            server.createContext("/keys", exchange -> {
                calls.incrementAndGet();
                try {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    if (sendHeaders) exchange.sendResponseHeaders(200, keys.length);
                    entered.countDown();
                    if (stall.get() && !release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Test endpoint was not released");
                    if (!sendHeaders) exchange.sendResponseHeaders(200, keys.length);
                    exchange.getResponseBody().write(keys);
                } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                catch (java.io.IOException disconnected) { /* A timed-out client may close the connection. */ }
                finally { exchange.close(); }
            });
            server.start();
        }
        String base() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        void resume() { stall.set(false); release.countDown(); }
        @Override public void close() { resume(); server.stop(0); workers.shutdownNow(); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void stalledHeadersOrBodyTimeOutAndALaterRefreshCanRecover(boolean sendHeaders) throws Exception {
        try (var endpoint = new Endpoint(sendHeaders)) {
            var options = new MicrosoftOAuth2Properties(); options.setJwkReadTimeout(Duration.ofMillis(200));
            var decoder = new MicrosoftOAuth2ProviderAdapter(options, RestClient.builder()).idTokenDecoder(registration(endpoint.base()));
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class));
            assertThat(endpoint.entered.getCount()).isZero();
            endpoint.resume();
            assertThat(decoder.decode(token).getSubject()).isEqualTo("user");
            assertThat(endpoint.calls.get()).isEqualTo(2);
        }
    }

    @Test void concurrentLoginStopsWaitingBeforeTheActiveDownloadCompletes() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try (var endpoint = new Endpoint(false)) {
            var options = new MicrosoftOAuth2Properties(); options.setJwkRefreshWaitTimeout(Duration.ofMillis(150));
            var decoder = new MicrosoftOAuth2ProviderAdapter(options, RestClient.builder()).idTokenDecoder(registration(endpoint.base()));
            var downloading = executor.submit(() -> decoder.decode(token));
            assertThat(endpoint.entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class));
            assertThat(downloading.isDone()).isFalse();
            assertThat(endpoint.calls.get()).isEqualTo(1);
            endpoint.resume();
            assertThat(downloading.get(2, TimeUnit.SECONDS).getSubject()).isEqualTo("user");
            assertThat(decoder.decode(token).getSubject()).isEqualTo("user");
            assertThat(endpoint.calls.get()).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    @Test void interruptedRefreshWaitPreservesTheInterruptFlag() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try (var endpoint = new Endpoint(false)) {
            var decoder = new MicrosoftOAuth2ProviderAdapter(new MicrosoftOAuth2Properties(), RestClient.builder())
                    .idTokenDecoder(registration(endpoint.base()));
            var downloading = executor.submit(() -> decoder.decode(token));
            assertThat(endpoint.entered.await(2, TimeUnit.SECONDS)).isTrue();
            var interrupted = new AtomicBoolean(); var failure = new AtomicReference<Throwable>();
            var waiter = new Thread(() -> {
                try { decoder.decode(token); } catch (Throwable invalid) { failure.set(invalid); }
                interrupted.set(Thread.currentThread().isInterrupted());
            });
            try {
                waiter.start();
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> waiter.getState() == Thread.State.TIMED_WAITING);
                waiter.interrupt(); waiter.join(2000);
                assertThat(waiter.isAlive()).isFalse();
                assertThat(failure.get()).isInstanceOf(JwtException.class);
                assertThat(interrupted.get()).isTrue();
            } finally { waiter.interrupt(); endpoint.resume(); waiter.join(2000); }
            assertThat(downloading.get(2, TimeUnit.SECONDS).getSubject()).isEqualTo("user");
        } finally { executor.shutdownNow(); }
    }

    @Test void defaultTransportHasFiniteTimeoutsAndCustomTransportIsHonored() {
        var runner = new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(MicrosoftOAuth2AutoConfiguration.class))
                .withPropertyValues("moli.oauth.client.enabled=true");
        runner.withPropertyValues("moli.oauth.client.microsoft.jwk-connect-timeout=2s", "moli.oauth.client.microsoft.jwk-read-timeout=3s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var factory = context.getBean("microsoftJwkSetRequestFactory", JdkClientHttpRequestFactory.class);
                    assertThat(((HttpClient) ReflectionTestUtils.getField(factory, "httpClient")).connectTimeout()).contains(Duration.ofSeconds(2));
                    assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(Duration.ofSeconds(3));
                });
        var custom = new JdkClientHttpRequestFactory(); custom.setReadTimeout(Duration.ofSeconds(1));
        runner.withBean("microsoftJwkSetRequestFactory", ClientHttpRequestFactory.class, () -> custom).run(context -> {
            assertThat(context).hasNotFailed();
            Object http = ReflectionTestUtils.getField(context.getBean(MicrosoftOAuth2ProviderAdapter.class), "http");
            assertThat(ReflectionTestUtils.getField(http, "clientRequestFactory")).isSameAs(custom);
        });
    }

    @ParameterizedTest @CsvSource({"jwk-connect-timeout,0ms", "jwk-read-timeout,-1s", "jwk-refresh-wait-timeout,0ms",
            "jwk-connect-timeout,PT0.000001S", "jwk-read-timeout,PT0.000001S", "jwk-refresh-wait-timeout,PT0.000001S"})
    void invalidTimeoutConfigurationFailsAtStartup(String property, String value) {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(MicrosoftOAuth2AutoConfiguration.class))
                .withPropertyValues("moli.oauth.client.enabled=true", "moli.oauth.client.microsoft." + property + "=" + value)
                .run(context -> assertThat(context).hasFailed().getFailure().hasRootCauseMessage(
                        "Microsoft " + property + " must be a positive duration of at least 1ms"));
    }
}
