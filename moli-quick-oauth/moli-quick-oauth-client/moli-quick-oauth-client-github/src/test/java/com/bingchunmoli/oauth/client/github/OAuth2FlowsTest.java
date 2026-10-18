package com.bingchunmoli.oauth.client.github;

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
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class OAuth2FlowsTest {
    static HttpServer server;
    static String base;
    static RSAKey key;
    static final AtomicReference<String> nonce = new AtomicReference<>();
    static final AtomicInteger credentialsCalls = new AtomicInteger(), refreshCalls = new AtomicInteger();
    static final AtomicReference<OidcUser> oidcUser = new AtomicReference<>();
    static final AtomicReference<Exception> failure = new AtomicReference<>();
    @BeforeAll static void start() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("test-key").generate();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/jwks", exchange -> json(exchange, new JWKSet(key.toPublicJWK()).toJSONObject()));
        server.createContext("/token", exchange -> {
            String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> data = new LinkedHashMap<>(Map.of("access_token", "access", "token_type", "Bearer", "expires_in", 3600));
            if (form.contains("client_credentials")) credentialsCalls.incrementAndGet();
            else if (form.contains("refresh_token")) {
                refreshCalls.incrementAndGet(); data.put("refresh_token", "rotated"); data.put("refresh_token_expires_in", 7200);
            } else {
                try {
                    var claims = new JWTClaimsSet.Builder().issuer(base).subject("stable-subject").audience("id")
                            .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(300)))
                            .claim("nonce", nonce.get()).claim("name", "测试用户").build();
                    SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
                    jwt.sign(new RSASSASigner(key));
                    data.put("id_token", jwt.serialize());
                } catch (JOSEException ex) { throw new java.io.IOException(ex); }
            }
            json(exchange, data);
        });
        server.createContext("/api", exchange -> json(exchange, Map.of("authorization", exchange.getRequestHeaders().getFirst("Authorization"))));
        server.start();
    }
    static void json(HttpExchange exchange, Object value) throws java.io.IOException {
        byte[] bytes = JsonMapper.builder().build().writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
    }
    @AfterAll static void stop() { server.stop(0); }
    @Configuration(proxyBeanMethods = false)
    static class Registrations {
        @Bean ClientRegistrationRepository registrations() {
            var app = ClientRegistration.withRegistrationId("app").clientId("id").clientSecret("secret")
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).tokenUri(base + "/token").build();
            var oidc = ClientRegistration.withRegistrationId("oidc").clientId("id").clientSecret("secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "profile").authorizationUri(base + "/authorize").tokenUri(base + "/token")
                    .issuerUri(base).jwkSetUri(base + "/jwks").userNameAttributeName("sub").build();
            return new InMemoryClientRegistrationRepository(app, oidc);
        }
    }
    @Configuration(proxyBeanMethods = false) @EnableWebSecurity
    static class Host {
        @Bean(name = "quickOAuth2FailureHandler")
        org.springframework.security.web.authentication.AuthenticationFailureHandler failureHandler() {
            return (request, response, exception) -> { failure.set(exception); response.sendError(401); };
        }
        @Bean SecurityFilterChain chain(HttpSecurity http, QuickOAuth2WebSupport support) {
            http.authorizeHttpRequests(r -> r.anyRequest().permitAll());
            http.with(support.configurer(), Customizer.withDefaults()); return http.build();
        }
        @Bean OAuth2LoginHandler handler() {
            return (identity, user, ref, request, response) -> {
                assertThat(user).isInstanceOf(OidcUser.class);
                oidcUser.set((OidcUser) user);
                assertThat(identity.subject()).isEqualTo(base + "|stable-subject");
                response.setStatus(204);
            };
        }
    }
    @Test void applicationTokensAndRefreshUseNativeProtocolClients() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(QuickOAuth2AutoConfiguration.class))
                .withUserConfiguration(Registrations.class).withPropertyValues("moli.oauth.client.enabled=true").run(context -> {
                    var quick = context.getBean(QuickOAuth2Client.class);
                    var app = quick.application("app");
                    var rest = quick.restClient(app, RestClient.builder().baseUrl(base));
                    assertThat(rest.get().uri("/api").retrieve().body(String.class)).contains("Bearer access");
                    quick.authorize(app);
                    assertThat(credentialsCalls).hasValue(1);
                    var registration = context.getBean(ClientRegistrationRepository.class).findByRegistrationId("oidc");
                    var service = context.getBean(OAuth2AuthorizedClientService.class);
                    service.saveAuthorizedClient(new OAuth2AuthorizedClient(registration, "user",
                            new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "expired", Instant.now().minusSeconds(100), Instant.now().minusSeconds(10)),
                            new OAuth2RefreshToken("refresh", Instant.now().minusSeconds(100))), QuickOAuth2Client.principal("user"));
                    var refreshed = quick.authorize(new AuthorizedClientRef("oidc", "user"));
                    assertThat(refreshed.getRefreshToken().getTokenValue()).isEqualTo("rotated");
                    assertThat(refreshed.getRefreshToken().getExpiresAt()).isAfter(Instant.now().plusSeconds(7000));
                    assertThat(refreshCalls).hasValue(1);
                });
    }
    @Test void oidcValidatesSignedIdTokenAndPreservesNativePrincipal() {
        new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(QuickOAuth2AutoConfiguration.class, QuickOAuth2WebAutoConfiguration.class))
                .withUserConfiguration(Registrations.class, Host.class)
                .withPropertyValues("moli.oauth.client.enabled=true", "moli.oauth.client.login.enabled=true").run(context -> {
                    assertThat(context).hasNotFailed();
                    var filters = context.getBean(FilterChainProxy.class);
                    var start = request("/oauth2/authorization/oidc"); var redirect = new MockHttpServletResponse();
                    filters.doFilter(start, redirect, new MockFilterChain());
                    var params = UriComponentsBuilder.fromUriString(redirect.getRedirectedUrl()).build().getQueryParams();
                    nonce.set(URLDecoder.decode(params.getFirst("nonce"), StandardCharsets.UTF_8));
                    var callback = request("/login/oauth2/code/oidc");
                    callback.setSession(start.getSession(false));
                    callback.setParameter("state", URLDecoder.decode(params.getFirst("state"), StandardCharsets.UTF_8));
                    callback.setParameter("code", "code"); var response = new MockHttpServletResponse();
                    filters.doFilter(callback, response, new MockFilterChain());
                    assertThat(failure.get()).isNull();
                    assertThat(response.getStatus()).isEqualTo(204);
                    assertThat(oidcUser.get().getIdToken().getSubject()).isEqualTo("stable-subject");
                    assertThat(callback.getSession(false).getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
                });
    }
    static MockHttpServletRequest request(String path) {
        var request = new MockHttpServletRequest("GET", path); request.setServletPath(path); return request;
    }
}
