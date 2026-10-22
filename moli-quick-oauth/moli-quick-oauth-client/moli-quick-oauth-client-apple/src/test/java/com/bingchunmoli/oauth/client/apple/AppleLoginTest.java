package com.bingchunmoli.oauth.client.apple;

import com.bingchunmoli.oauth.client.*;
import com.bingchunmoli.oauth.client.autoconfigure.*;
import com.bingchunmoli.oauth.client.web.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.*;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.*;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class AppleLoginTest {
    @TempDir static Path directory;
    static HttpServer server;
    static String base;
    static RSAKey key, wrongKey;
    static ECKey signingKey;
    static Path keyFile;
    static final AtomicReference<String> mode = new AtomicReference<>(), nonce = new AtomicReference<>(), form = new AtomicReference<>();
    static final AtomicReference<OAuth2Identity> identity = new AtomicReference<>();
    static final AtomicReference<OidcUser> nativeUser = new AtomicReference<>();
    static final AtomicReference<Throwable> tokenError = new AtomicReference<>();
    static final AtomicInteger tokenCalls = new AtomicInteger();
    @BeforeAll static void start() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("apple").generate(); wrongKey = new RSAKeyGenerator(2048).keyID("apple").generate();
        signingKey = new ECKeyGenerator(Curve.P_256).keyID("KEY1234567").generate();
        keyFile = directory.resolve("test-key.p8");
        Files.writeString(keyFile, "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(signingKey.toECPrivateKey().getEncoded()) + "\n-----END PRIVATE KEY-----\n");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/jwks", exchange -> json(exchange, new JWKSet(key.toPublicJWK()).toJSONObject()));
        server.createContext("/token", exchange -> {
            tokenCalls.incrementAndGet();
            String encoded = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); form.set(encoded);
            try {
                Map<String, String> parameters = new HashMap<>();
                for (String part : encoded.split("&")) {
                    String[] pair = part.split("=", 2);
                    parameters.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8));
                }
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isNull();
                var secret = SignedJWT.parse(parameters.get("client_secret"));
                assertThat(secret.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
                assertThat(secret.getHeader().getKeyID()).isEqualTo("KEY1234567");
                assertThat(secret.verify(new ECDSAVerifier(signingKey.toECPublicKey()))).isTrue();
                assertThat(secret.getJWTClaimsSet().getIssuer()).isEqualTo("TEAM123456");
                assertThat(secret.getJWTClaimsSet().getSubject()).isEqualTo("app");
                assertThat(secret.getJWTClaimsSet().getAudience()).containsExactly(AppleClientSecrets.ISSUER);
                assertThat(secret.getJWTClaimsSet().getExpirationTime().toInstant()).isAfter(Instant.now()).isBefore(Instant.now().plusSeconds(301));
                if ("refresh_token".equals(parameters.get("grant_type"))) {
                    json(exchange, Map.of("access_token", "refreshed-access", "token_type", "Bearer", "expires_in", 3600)); return;
                }
                String scenario = mode.get();
                var claims = new JWTClaimsSet.Builder().issuer("issuer".equals(scenario) ? "https://untrusted.test" : base)
                        .subject("apple-subject").audience("audience".equals(scenario) ? "wrong-app" : "app")
                        .issueTime(Date.from(Instant.now().minusSeconds(120)))
                        .expirationTime(Date.from("expired".equals(scenario) ? Instant.now().minusSeconds(120) : Instant.now().plusSeconds(300)))
                        .claim("nonce", "nonce".equals(scenario) ? "wrong-nonce" : nonce.get())
                        .claim("email", "relay@privaterelay.appleid.com").claim("email_verified", "unverified".equals(scenario) ? "false" : "true")
                        .claim("is_private_email", "true").build();
                var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("apple").build(), claims);
                jwt.sign(new RSASSASigner("signature".equals(scenario) ? wrongKey : key));
                json(exchange, Map.of("access_token", "private-access", "token_type", "Bearer", "expires_in", 3600,
                        "refresh_token", "private-refresh", "id_token", jwt.serialize()));
            } catch (Exception | AssertionError error) { tokenError.set(error); json(exchange, Map.of("error", "invalid_client")); }
        });
        server.start();
    }
    @AfterAll static void stop() { server.stop(0); }
    @BeforeEach void reset() { mode.set("ok"); identity.set(null); nativeUser.set(null); tokenError.set(null); tokenCalls.set(0); }
    static void json(HttpExchange exchange, Object value) throws java.io.IOException {
        byte[] data = JsonMapper.builder().build().writeValueAsBytes(value); exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, data.length); exchange.getResponseBody().write(data); exchange.close();
    }
    @Configuration(proxyBeanMethods = false)
    static class Registrations {
        @Bean ClientRegistrationRepository registrations() { return new InMemoryClientRegistrationRepository(registration()); }
    }
    static ClientRegistration registration() {
        return ClientRegistration.withRegistrationId("work").clientId("app").clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "name", "email").authorizationUri(base + "/authorize").tokenUri(base + "/token")
                .userNameAttributeName("sub").issuerUri(base).jwkSetUri(base + "/jwks").build();
    }
    @Configuration(proxyBeanMethods = false) @EnableWebSecurity
    static class Host {
        @Bean SecurityFilterChain chain(HttpSecurity http, QuickOAuth2WebSupport support,
                @org.springframework.beans.factory.annotation.Value("${test.disable-csrf:false}") boolean disableCsrf) {
            if (disableCsrf) http.csrf(org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer::disable);
            http.authorizeHttpRequests(r -> r.anyRequest().permitAll()); http.with(support.configurer(), Customizer.withDefaults()); return http.build();
        }
        @Bean OAuth2LoginHandler handler() {
            return (value, user, ref, request, response) -> { identity.set(value); nativeUser.set((OidcUser) user); response.setStatus(204); };
        }
    }
    static WebApplicationContextRunner runner() {
        return new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(AppleOAuth2AutoConfiguration.class,
                QuickOAuth2AutoConfiguration.class, QuickOAuth2WebAutoConfiguration.class)).withUserConfiguration(Registrations.class, Host.class)
                .withPropertyValues("moli.oauth.client.enabled=true", "moli.oauth.client.login.enabled=true", "moli.oauth.client.provider-mappings.work=apple",
                        "moli.oauth.client.apple.team-id=TEAM123456", "moli.oauth.client.apple.key-id=KEY1234567",
                        "moli.oauth.client.apple.private-key=" + keyFile.toUri());
    }
    static MockHttpServletRequest request(String method, String path) {
        var r = new MockHttpServletRequest(method, path); r.setServletPath(path); return r;
    }
    static MockHttpServletRequest callback(FilterChainProxy filters) throws Exception {
        var start = request("GET", "/oauth2/authorization/work"); var redirect = new MockHttpServletResponse();
        filters.doFilter(start, redirect, new MockFilterChain());
        var params = UriComponentsBuilder.fromUriString(redirect.getRedirectedUrl()).build().getQueryParams();
        assertThat(params.getFirst("response_mode")).isEqualTo("form_post");
        assertThat(URLDecoder.decode(params.getFirst("scope"), StandardCharsets.UTF_8)).isEqualTo("email name");
        nonce.set(URLDecoder.decode(params.getFirst("nonce"), StandardCharsets.UTF_8));
        var callback = request("POST", "/login/oauth2/code/work"); callback.setSession(start.getSession(false));
        callback.setContentType("application/x-www-form-urlencoded");
        callback.setParameter("state", URLDecoder.decode(params.getFirst("state"), StandardCharsets.UTF_8));
        callback.setParameter("code", "code");
        callback.setParameter("user", "{\"name\":{\"firstName\":\"Apple\",\"lastName\":\"User\"},\"email\":\"spoof@example.test\"}");
        return callback;
    }
    static MockHttpServletResponse send(FilterChainProxy filters, MockHttpServletRequest request) throws Exception {
        var response = new MockHttpServletResponse(); filters.doFilter(request, response, new MockFilterChain()); return response;
    }
    @Test void formPostLoginVerifiesIdTokenSignsClientSecretAndRefreshes() {
        runner().run(context -> {
            var filters = context.getBean(FilterChainProxy.class); var callback = callback(filters);
            assertThat(send(filters, callback).getStatus()).isEqualTo(204); assertThat(tokenError.get()).isNull();
            assertThat(identity.get().provider()).isEqualTo("apple"); assertThat(identity.get().subject()).isEqualTo(base + "|apple-subject");
            assertThat(identity.get().name()).isEqualTo("Apple User");
            assertThat(identity.get().email()).isEqualTo("relay@privaterelay.appleid.com"); assertThat(nativeUser.get().getIdToken()).isNotNull();
            assertThat(callback.getSession(false).getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
            var service = context.getBean(OAuth2AuthorizedClientService.class);
            OAuth2AuthorizedClient saved = service.loadAuthorizedClient("work", "apple-subject");
            service.saveAuthorizedClient(new OAuth2AuthorizedClient(saved.getClientRegistration(), saved.getPrincipalName(),
                    new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "expired", Instant.now().minusSeconds(120), Instant.now().minusSeconds(60)),
                    saved.getRefreshToken()), QuickOAuth2Client.principal(saved.getPrincipalName()));
            var refreshed = context.getBean(QuickOAuth2Client.class).authorize(new AuthorizedClientRef("work", "apple-subject"));
            assertThat(refreshed.getAccessToken().getTokenValue()).isEqualTo("refreshed-access");
            assertThat(form.get()).contains("grant_type=refresh_token"); assertThat(tokenError.get()).isNull();
            assertThat(send(filters, callback).getStatus()).isEqualTo(403);
        });
    }
    @ParameterizedTest @ValueSource(strings = {"issuer", "audience", "nonce", "signature", "expired"})
    void rejectsInvalidIdTokens(String scenario) {
        mode.set(scenario); runner().run(context -> {
            var filters = context.getBean(FilterChainProxy.class);
            assertThat(send(filters, callback(filters)).getStatus()).isEqualTo(401); assertThat(identity.get()).isNull(); assertThat(tokenError.get()).isNull();
        });
    }
    @Test void unverifiedEmailAndMissingOneTimeNameRemainAbsent() {
        mode.set("unverified"); runner().run(context -> {
            var filters = context.getBean(FilterChainProxy.class); var callback = callback(filters); callback.removeParameter("user");
            assertThat(send(filters, callback).getStatus()).isEqualTo(204);
            assertThat(identity.get().email()).isNull(); assertThat(identity.get().name()).isNull();
        });
    }
    @ParameterizedTest @ValueSource(strings = {"state", "browser", "path", "duplicate-state", "business"})
    void invalidCallbacksAndBusinessPostsRetainCsrfProtection(String scenario) {
        runner().run(context -> {
            var filters = context.getBean(FilterChainProxy.class); var callback = callback(filters);
            switch (scenario) {
                case "state" -> callback.setParameter("state", "unknown");
                case "browser" -> callback.setSession(null);
                case "path" -> { callback.setRequestURI("/login/oauth2/code/other"); callback.setServletPath("/login/oauth2/code/other"); }
                case "duplicate-state" -> callback.addParameter("state", "other");
                default -> { callback.setRequestURI("/business"); callback.setServletPath("/business"); }
            }
            assertThat(send(filters, callback).getStatus()).isEqualTo(403); assertThat(tokenCalls.get()).isZero();
        });
    }
    @Test void missingPrivateKeyFailsAtStartupWithoutDisclosingItsContents() {
        runner().withPropertyValues("moli.oauth.client.apple.private-key=file:/nonexistent/apple-test.p8").run(context ->
                assertThat(context).hasFailed());
    }
    @Test void hostDisabledCsrfIsNotReenabled() {
        runner().withPropertyValues("test.disable-csrf=true").run(context -> {
            var response = send(context.getBean(FilterChainProxy.class), request("POST", "/business"));
            assertThat(response.getStatus()).isEqualTo(200); assertThat(tokenCalls.get()).isZero();
        });
    }
    @Test void browserNameMarkupIsNotAcceptedAsDisplayName() {
        runner().run(context -> {
            var filters = context.getBean(FilterChainProxy.class); var callback = callback(filters);
            callback.setParameter("user", "{\"name\":{\"firstName\":\"<script>\",\"lastName\":\"Safe\"}}");
            assertThat(send(filters, callback).getStatus()).isEqualTo(204); assertThat(identity.get().name()).isEqualTo("Safe");
        });
    }
    @Test void registrationDefaultsWorkWithGeneratedSecretAndNoStaticSecret() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(AppleOAuth2AutoConfiguration.class, QuickOAuth2AutoConfiguration.class))
                .withPropertyValues("moli.oauth.client.enabled=true", "spring.security.oauth2.client.registration.apple.client-id=app",
                        "moli.oauth.client.apple.team-id=TEAM123456", "moli.oauth.client.apple.key-id=KEY1234567",
                        "moli.oauth.client.apple.private-key=" + keyFile.toUri()).run(context -> {
                    var registration = context.getBean(ClientRegistrationRepository.class).findByRegistrationId("apple");
                    assertThat(registration.getProviderDetails().getIssuerUri()).isEqualTo(AppleClientSecrets.ISSUER);
                    assertThat(registration.getScopes()).contains("openid", "name", "email");
                });
    }
}
