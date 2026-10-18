package com.bingchunmoli.oauth.client.google;

import com.bingchunmoli.oauth.client.*;
import com.bingchunmoli.oauth.client.autoconfigure.*;
import com.bingchunmoli.oauth.client.web.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
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

class GoogleLoginTest {
    static HttpServer server;
    static String base;
    static RSAKey key, wrongKey;
    static final AtomicReference<String> nonce = new AtomicReference<>(), scenario = new AtomicReference<>("ok"), tokenForm = new AtomicReference<>();
    static final AtomicReference<OAuth2Identity> identity = new AtomicReference<>();
    static final AtomicReference<OidcUser> nativeUser = new AtomicReference<>();
    static final AtomicReference<Exception> failure = new AtomicReference<>();
    @BeforeAll static void start() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("key").generate(); wrongKey = new RSAKeyGenerator(2048).keyID("key").generate();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/.well-known/openid-configuration", exchange -> json(exchange, Map.of(
                "issuer", base, "authorization_endpoint", base + "/authorize", "token_endpoint", base + "/token",
                "userinfo_endpoint", base + "/user", "jwks_uri", base + "/jwks", "response_types_supported", List.of("code"),
                "subject_types_supported", List.of("public"), "id_token_signing_alg_values_supported", List.of("RS256"),
                "token_endpoint_auth_methods_supported", List.of("client_secret_basic"))));
        server.createContext("/jwks", exchange -> json(exchange, new JWKSet(key.toPublicJWK()).toJSONObject()));
        server.createContext("/token", exchange -> {
            tokenForm.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (tokenForm.get().contains("grant_type=refresh_token")) {
                json(exchange, Map.of("access_token", "new-access", "token_type", "Bearer", "expires_in", 3600)); return;
            }
            try {
                String mode = scenario.get();
                var claims = new JWTClaimsSet.Builder().issuer(mode.equals("issuer") ? "https://untrusted.test" : base)
                        .subject(mode.equals("another-user") ? "another-subject" : "stable-subject").audience(mode.equals("audience") ? "another-app" : "app")
                        .issueTime(Date.from(Instant.now().minusSeconds(120)))
                        .expirationTime(Date.from(mode.equals("expired") ? Instant.now().minusSeconds(120) : Instant.now().plusSeconds(300)))
                        .claim("nonce", mode.equals("nonce") ? "wrong-nonce" : nonce.get()).claim("name", "谷歌用户")
                        .claim("picture", "https://example.test/avatar").claim("email", "user@example.test").claim("email_verified", !mode.equals("unverified")).build();
                var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("key").build(), claims);
                jwt.sign(new RSASSASigner(mode.equals("signature") ? wrongKey : key));
                var data = new LinkedHashMap<String, Object>(Map.of("access_token", mode.equals("ok") ? "private-access" : "second-access",
                        "token_type", "Bearer", "expires_in", 3600, "id_token", jwt.serialize()));
                if (!Set.of("no-refresh", "another-user").contains(mode)) {
                    data.put("refresh_token", mode.startsWith("rotated-") ? "new-login-refresh" : "private-refresh");
                    if (!Set.of("same-refresh", "rotated-unknown-expiry").contains(mode)) data.put("refresh_token_expires_in", 7200);
                }
                json(exchange, data);
            } catch (JOSEException ex) { throw new java.io.IOException(ex); }
        });
        server.createContext("/user", exchange -> json(exchange, Map.of("sub", scenario.get().equals("userinfo-sub") ? "wrong-subject" :
                scenario.get().equals("another-user") ? "another-subject" : "stable-subject")));
        server.start();
    }
    @AfterAll static void stop() { server.stop(0); }
    @BeforeEach void reset() { scenario.set("ok"); identity.set(null); failure.set(null); nativeUser.set(null); }
    static void json(HttpExchange exchange, Object data) throws java.io.IOException {
        byte[] bytes = JsonMapper.builder().build().writeValueAsBytes(data); exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
    }
    @Configuration(proxyBeanMethods = false)
    static class Registrations {
        @Bean ClientRegistrationRepository registrations() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("work").clientId("app").clientSecret("secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "profile", "email").authorizationUri(base + "/authorize").tokenUri(base + "/token")
                    .userInfoUri(base + "/user").userNameAttributeName("sub").issuerUri(base).jwkSetUri(base + "/jwks").build());
        }
    }
    @Configuration(proxyBeanMethods = false) @EnableWebSecurity
    static class Host {
        @Bean SecurityFilterChain chain(HttpSecurity http, QuickOAuth2WebSupport support) {
            http.authorizeHttpRequests(r -> r.anyRequest().permitAll()); http.with(support.configurer(), Customizer.withDefaults()); return http.build();
        }
        @Bean OAuth2LoginHandler handler() {
            return (value, user, ref, request, response) -> { identity.set(value); nativeUser.set((OidcUser) user); response.setStatus(204); };
        }
        @Bean(name = "quickOAuth2FailureHandler") org.springframework.security.web.authentication.AuthenticationFailureHandler failureHandler() {
            return (request, response, error) -> { failure.set(error); response.sendError(401); };
        }
    }
    static WebApplicationContextRunner runner() {
        return new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(GoogleOAuth2AutoConfiguration.class,
                QuickOAuth2AutoConfiguration.class, QuickOAuth2WebAutoConfiguration.class)).withUserConfiguration(Registrations.class, Host.class)
                .withPropertyValues("moli.oauth.client.enabled=true", "moli.oauth.client.login.enabled=true",
                        "moli.oauth.client.provider-mappings.work=google", "moli.oauth.client.google.offline=true", "moli.oauth.client.google.prompt=consent");
    }
    static MockHttpServletRequest request(String path) { var r = new MockHttpServletRequest("GET", path); r.setServletPath(path); return r; }
    static MockHttpServletRequest callback(FilterChainProxy filters) throws Exception {
        var start = request("/oauth2/authorization/work"); var redirect = new MockHttpServletResponse(); filters.doFilter(start, redirect, new MockFilterChain());
        var params = UriComponentsBuilder.fromUriString(redirect.getRedirectedUrl()).build().getQueryParams();
        assertThat(params.getFirst("code_challenge_method")).isEqualTo("S256"); assertThat(params.getFirst("access_type")).isEqualTo("offline");
        assertThat(params.getFirst("prompt")).isEqualTo("consent"); nonce.set(URLDecoder.decode(params.getFirst("nonce"), StandardCharsets.UTF_8));
        var callback = request("/login/oauth2/code/work"); callback.setSession(start.getSession(false));
        callback.setParameter("state", URLDecoder.decode(params.getFirst("state"), StandardCharsets.UTF_8)); callback.setParameter("code", "code"); return callback;
    }
    @Test void signedOidcLoginPreservesNativeUserAndRefreshesWithoutBusinessSession() {
        runner().run(context -> {
            var filters = context.getBean(FilterChainProxy.class); var callback = callback(filters); var response = new MockHttpServletResponse();
            filters.doFilter(callback, response, new MockFilterChain()); assertThat(failure.get()).isNull(); assertThat(response.getStatus()).isEqualTo(204);
            assertThat(identity.get().provider()).isEqualTo("google"); assertThat(identity.get().subject()).isEqualTo(base + "|stable-subject");
            assertThat(identity.get().email()).isEqualTo("user@example.test"); assertThat(nativeUser.get().getIdToken()).isNotNull();
            assertThat(tokenForm.get()).contains("code_verifier="); assertThat(callback.getSession(false).getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
            var service = context.getBean(OAuth2AuthorizedClientService.class); OAuth2AuthorizedClient saved = service.loadAuthorizedClient("work", "stable-subject");
            service.saveAuthorizedClient(new OAuth2AuthorizedClient(saved.getClientRegistration(), saved.getPrincipalName(),
                    new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "expired", Instant.now().minusSeconds(120), Instant.now().minusSeconds(60)),
                    saved.getRefreshToken()), QuickOAuth2Client.principal(saved.getPrincipalName()));
            var refreshed = context.getBean(QuickOAuth2Client.class).authorize(new AuthorizedClientRef("work", "stable-subject"));
            assertThat(refreshed.getAccessToken().getTokenValue()).isEqualTo("new-access");
            assertThat(refreshed.getRefreshToken().getTokenValue()).isEqualTo("private-refresh"); assertThat(tokenForm.get()).contains("grant_type=refresh_token");
        });
    }
    @ParameterizedTest @ValueSource(strings = {"nonce", "issuer", "audience", "expired", "signature", "userinfo-sub"})
    void nativeOidcValidationRejectsInvalidClaimsAndSignature(String mode) {
        scenario.set(mode); runner().run(context -> {
            var filters = context.getBean(FilterChainProxy.class); var callback = callback(filters); var response = new MockHttpServletResponse();
            filters.doFilter(callback, response, new MockFilterChain()); assertThat(response.getStatus()).isEqualTo(401);
            assertThat(identity.get()).isNull(); assertThat(failure.get()).isNotNull();
        });
    }
    @ParameterizedTest @ValueSource(strings = {"no-refresh", "same-refresh", "rotated-login", "rotated-unknown-expiry", "another-user"})
    void repeatedLoginPreservesRefreshOnlyForTheSameIdentity(String mode) {
        runner().run(context -> {
            var filters = context.getBean(FilterChainProxy.class);
            var firstResponse = new MockHttpServletResponse();
            filters.doFilter(callback(filters), firstResponse, new MockFilterChain());
            assertThat(firstResponse.getStatus()).isEqualTo(204);
            var service = context.getBean(OAuth2AuthorizedClientService.class);
            OAuth2AuthorizedClient first = service.loadAuthorizedClient("work", "stable-subject");
            assertThat(first.getRefreshToken().getExpiresAt()).isNotNull();
            scenario.set(mode);
            var secondResponse = new MockHttpServletResponse();
            filters.doFilter(callback(filters), secondResponse, new MockFilterChain());
            assertThat(failure.get()).isNull(); assertThat(secondResponse.getStatus()).isEqualTo(204);
            OAuth2AuthorizedClient second = service.loadAuthorizedClient("work", mode.equals("another-user") ? "another-subject" : "stable-subject");
            assertThat(second.getAccessToken().getTokenValue()).isEqualTo("second-access");
            if (mode.equals("another-user")) {
                assertThat(second.getRefreshToken()).isNull();
                assertThat(((OAuth2AuthorizedClient) service.loadAuthorizedClient("work", "stable-subject")).getRefreshToken().getTokenValue())
                        .isEqualTo("private-refresh");
            } else if (mode.startsWith("rotated-")) {
                assertThat(second.getRefreshToken().getTokenValue()).isEqualTo("new-login-refresh");
                if (mode.equals("rotated-unknown-expiry")) assertThat(second.getRefreshToken().getExpiresAt()).isNull();
                else assertThat(second.getRefreshToken().getExpiresAt()).isNotNull();
            } else {
                assertThat(second.getRefreshToken()).isNotNull();
                assertThat(second.getRefreshToken().getTokenValue()).isEqualTo(first.getRefreshToken().getTokenValue());
                assertThat(second.getRefreshToken().getExpiresAt()).isEqualTo(first.getRefreshToken().getExpiresAt());
                assertThat(second.getRefreshToken().getIssuedAt()).isEqualTo(first.getRefreshToken().getIssuedAt());
            }
        });
    }
    @Test void unverifiedEmailIsNotExposedAsVerifiedIdentityEmail() {
        scenario.set("unverified"); runner().run(context -> {
            var filters = context.getBean(FilterChainProxy.class); var response = new MockHttpServletResponse();
            filters.doFilter(callback(filters), response, new MockFilterChain()); assertThat(response.getStatus()).isEqualTo(204); assertThat(identity.get().email()).isNull();
        });
    }
    @Test void defaultsAreOfflineAtStartupAndSupportProviderAliases() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(GoogleOAuth2AutoConfiguration.class, QuickOAuth2AutoConfiguration.class))
                .withPropertyValues("moli.oauth.client.enabled=true", "spring.security.oauth2.client.registration.one.provider=google",
                        "spring.security.oauth2.client.registration.one.client-id=one", "spring.security.oauth2.client.registration.one.client-secret=s",
                        "spring.security.oauth2.client.registration.two.provider=custom-google", "moli.oauth.client.provider-mappings.two=google",
                        "spring.security.oauth2.client.registration.two.client-id=two", "spring.security.oauth2.client.registration.two.client-secret=s")
                .run(context -> {
                    assertThat(context).hasNotFailed(); var registrations = context.getBean(ClientRegistrationRepository.class);
                    for (String id : List.of("one", "two")) {
                        var registration = registrations.findByRegistrationId(id);
                        assertThat(registration.getScopes()).containsExactlyInAnyOrder("openid", "profile", "email");
                        assertThat(registration.getProviderDetails().getIssuerUri()).isEqualTo("https://accounts.google.com");
                        assertThat(registration.getProviderDetails().getJwkSetUri()).isEqualTo("https://www.googleapis.com/oauth2/v3/certs");
                    }
                });
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(GoogleOAuth2AutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(GoogleOAuth2ProviderAdapter.class));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void explicitIssuerUsesDiscoveredEndpointsAndPreservesExplicitOverrides(boolean overrideAuthorization) {
        var runner = new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(GoogleOAuth2AutoConfiguration.class,
                QuickOAuth2AutoConfiguration.class, QuickOAuth2WebAutoConfiguration.class)).withUserConfiguration(Host.class)
                .withPropertyValues("moli.oauth.client.enabled=true", "moli.oauth.client.login.enabled=true",
                        "moli.oauth.client.google.offline=true", "moli.oauth.client.google.prompt=consent",
                        "spring.security.oauth2.client.registration.work.provider=google",
                        "spring.security.oauth2.client.registration.work.client-id=app",
                        "spring.security.oauth2.client.registration.work.client-secret=secret",
                        "spring.security.oauth2.client.provider.google.issuer-uri=" + base);
        if (overrideAuthorization) runner = runner.withPropertyValues("spring.security.oauth2.client.provider.google.authorization-uri=" + base + "/explicit-authorize",
                "spring.security.oauth2.client.registration.work.client-authentication-method=client_secret_post");
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var registration = context.getBean(ClientRegistrationRepository.class).findByRegistrationId("work");
            assertThat(registration.getClientAuthenticationMethod()).isEqualTo(overrideAuthorization ?
                    ClientAuthenticationMethod.CLIENT_SECRET_POST : ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
            var provider = registration.getProviderDetails();
            assertThat(provider.getTokenUri()).isEqualTo(base + "/token");
            assertThat(provider.getJwkSetUri()).isEqualTo(base + "/jwks");
            assertThat(provider.getUserInfoEndpoint().getUri()).isEqualTo(base + "/user");
            assertThat(provider.getAuthorizationUri()).isEqualTo(base + (overrideAuthorization ? "/explicit-authorize" : "/authorize"));
            var filters = context.getBean(FilterChainProxy.class); var response = new MockHttpServletResponse();
            filters.doFilter(callback(filters), response, new MockFilterChain());
            assertThat(failure.get()).isNull(); assertThat(response.getStatus()).isEqualTo(204);
            assertThat(identity.get().subject()).isEqualTo(base + "|stable-subject");
        });
    }
    @Test void missingOpenidFailsAtStartupInsteadOfFallingBackToOauthUserInfo() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(GoogleOAuth2AutoConfiguration.class, QuickOAuth2AutoConfiguration.class))
                .withPropertyValues("moli.oauth.client.enabled=true", "spring.security.oauth2.client.registration.google.client-id=app",
                        "spring.security.oauth2.client.registration.google.client-secret=s", "spring.security.oauth2.client.registration.google.scope=email")
                .run(context -> assertThat(context).hasFailed());
    }
}
