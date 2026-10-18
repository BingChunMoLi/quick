package com.bingchunmoli.oauth.client.qq;

import com.bingchunmoli.oauth.client.*;
import com.bingchunmoli.oauth.client.autoconfigure.*;
import com.bingchunmoli.oauth.client.web.*;
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
import org.springframework.security.web.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class QqLoginTest {
    static HttpServer server;
    static String base;
    static final AtomicReference<OAuth2Identity> identity = new AtomicReference<>();
    static final AtomicReference<Exception> failure = new AtomicReference<>();
    static final AtomicReference<MultiValueMap<String, String>> tokenQuery = new AtomicReference<>();
    static final AtomicReference<String> scenario = new AtomicReference<>("ok");
    static final AtomicInteger tokenCalls = new AtomicInteger();
    @BeforeAll static void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/token", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            tokenQuery.set(params(base + exchange.getRequestURI())); tokenCalls.incrementAndGet();
            if (scenario.get().equals("transport-error")) {
                exchange.sendResponseHeaders(503, -1); exchange.close(); return;
            }
            if (scenario.get().equals("refresh-error")) json(exchange, Map.of("error", 100036, "error_description", "private-refresh"));
            else if (scenario.get().equals("token-error")) json(exchange, Map.of("error", 100013, "error_description", "secret&+?"));
            else if (scenario.get().equals("malformed-token")) json(exchange, Map.of("access_token", "private-access"));
            else json(exchange, Map.of("access_token", "private-access", "expires_in", 3600, "refresh_token", "rotated", "scope", "get_user_info,list_album"));
        });
        server.createContext("/me", exchange -> {
            assertThat(params(base + exchange.getRequestURI()).getFirst("fmt")).isEqualTo("json");
            if (scenario.get().equals("jsonp")) {
                byte[] jsonp = "callback({\"openid\":\"open-id\",\"client_id\":\"app\"});".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, jsonp.length); exchange.getResponseBody().write(jsonp); exchange.close(); return;
            }
            json(exchange, Map.of("client_id", scenario.get().equals("wrong-app") ? "another-app" : "app", "openid", "open-id"));
        });
        server.createContext("/user", exchange -> {
            var query = params(base + exchange.getRequestURI());
            assertThat(query.getFirst("access_token")).isEqualTo("private-access");
            assertThat(query.getFirst("oauth_consumer_key")).isEqualTo("app");
            assertThat(query.getFirst("openid")).isEqualTo("open-id");
            json(exchange, scenario.get().equals("profile-error") ? Map.of("ret", 1002, "msg", "private-access") :
                    Map.of("ret", 0, "nickname", "测试昵称", "figureurl_qq_2", "https://example.test/avatar"));
        });
        server.start();
    }
    @AfterAll static void stop() { server.stop(0); }
    @BeforeEach void reset() { scenario.set("ok"); identity.set(null); failure.set(null); tokenCalls.set(0); }
    static void json(HttpExchange exchange, Object data) throws java.io.IOException {
        byte[] bytes = JsonMapper.builder().build().writeValueAsBytes(data);
        // QQ endpoints may label their JSON text/plain.
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
    }
    static MultiValueMap<String, String> params(String uri) {
        var values = UriComponentsBuilder.fromUriString(uri).build().getQueryParams();
        var decoded = new org.springframework.util.LinkedMultiValueMap<String, String>();
        values.forEach((key, list) -> list.forEach(value -> decoded.add(key, URLDecoder.decode(value, StandardCharsets.UTF_8))));
        return decoded;
    }
    @Configuration(proxyBeanMethods = false) @EnableWebSecurity
    static class Host {
        @Bean SecurityFilterChain chain(HttpSecurity http, QuickOAuth2WebSupport support) {
            http.authorizeHttpRequests(r -> r.anyRequest().permitAll());
            http.with(support.configurer(), Customizer.withDefaults()); return http.build();
        }
        @Bean OAuth2LoginHandler handler() {
            return (value, user, ref, request, response) -> { identity.set(value); response.setStatus(204); };
        }
        @Bean(name = "quickOAuth2FailureHandler") org.springframework.security.web.authentication.AuthenticationFailureHandler failureHandler() {
            return (request, response, error) -> { failure.set(error); response.sendError(401); };
        }
    }
    static WebApplicationContextRunner runner() {
        return new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(QqOAuth2AutoConfiguration.class,
                QuickOAuth2AutoConfiguration.class, QuickOAuth2WebAutoConfiguration.class)).withUserConfiguration(Host.class)
                .withPropertyValues("moli.oauth.client.enabled=true", "moli.oauth.client.login.enabled=true",
                        "moli.oauth.client.qq.open-id-uri=" + base + "/me",
                        "spring.security.oauth2.client.registration.work.provider=qq",
                        "spring.security.oauth2.client.registration.work.client-id=app",
                        "spring.security.oauth2.client.registration.work.client-secret=secret&+?",
                        "spring.security.oauth2.client.registration.work.scope=get_user_info,list_album",
                        "spring.security.oauth2.client.provider.qq.authorization-uri=" + base + "/authorize",
                        "spring.security.oauth2.client.provider.qq.token-uri=" + base + "/token",
                        "spring.security.oauth2.client.provider.qq.user-info-uri=" + base + "/user");
    }
    static MockHttpServletRequest request(String path) { var r = new MockHttpServletRequest("GET", path); r.setServletPath(path); return r; }
    static MockHttpServletRequest startCallback(FilterChainProxy filters) throws Exception {
        var start = request("/oauth2/authorization/work"); var response = new MockHttpServletResponse();
        filters.doFilter(start, response, new MockFilterChain());
        var query = params(response.getRedirectedUrl());
        assertThat(query.getFirst("scope")).isEqualTo("get_user_info,list_album");
        var callback = request("/login/oauth2/code/work"); callback.setSession(start.getSession(false));
        callback.setParameter("state", query.getFirst("state")); callback.setParameter("code", "code&+?"); return callback;
    }
    @Test void loginAndRefreshUseQqProtocolAndApplicationScopedIdentity() {
        runner().run(context -> {
            var filters = context.getBean(FilterChainProxy.class); var callback = startCallback(filters);
            var response = new MockHttpServletResponse(); filters.doFilter(callback, response, new MockFilterChain());
            assertThat(failure.get()).isNull(); assertThat(response.getStatus()).isEqualTo(204);
            assertThat(identity.get().subject()).isEqualTo("app|open-id"); assertThat(identity.get().name()).isEqualTo("测试昵称");
            assertThat(identity.get().attributes()).doesNotContainKeys("access_token", "refresh_token", "client_secret");
            assertThat(tokenQuery.get().getFirst("code")).isEqualTo("code&+?");
            assertThat(tokenQuery.get().getFirst("client_secret")).isEqualTo("secret&+?");
            assertThat(callback.getSession(false).getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
            var service = context.getBean(OAuth2AuthorizedClientService.class);
            OAuth2AuthorizedClient saved = service.loadAuthorizedClient("work", "app|open-id"); assertThat(saved).isNotNull();
            service.saveAuthorizedClient(new OAuth2AuthorizedClient(saved.getClientRegistration(), saved.getPrincipalName(),
                    new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "expired", Instant.now().minusSeconds(120), Instant.now().minusSeconds(60)),
                    saved.getRefreshToken()), QuickOAuth2Client.principal(saved.getPrincipalName()));
            var refreshed = context.getBean(QuickOAuth2Client.class).authorize(new AuthorizedClientRef("work", saved.getPrincipalName()));
            assertThat(refreshed.getRefreshToken().getTokenValue()).isEqualTo("rotated");
            assertThat(tokenQuery.get().getFirst("grant_type")).isEqualTo("refresh_token");
            assertThat(tokenQuery.get().getFirst("refresh_token")).isEqualTo("rotated");
            service.saveAuthorizedClient(new OAuth2AuthorizedClient(saved.getClientRegistration(), saved.getPrincipalName(),
                    new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "expired", Instant.now().minusSeconds(120), Instant.now().minusSeconds(60)),
                    refreshed.getRefreshToken()), QuickOAuth2Client.principal(saved.getPrincipalName()));
            scenario.set("refresh-error");
            assertThatThrownBy(() -> context.getBean(QuickOAuth2Client.class).authorize(new AuthorizedClientRef("work", saved.getPrincipalName())))
                    .isInstanceOf(AuthorizationRequiredException.class).hasMessageNotContaining("private-refresh");
            assertThat((OAuth2AuthorizedClient) service.loadAuthorizedClient("work", saved.getPrincipalName())).isNull();
        });
    }
    @ParameterizedTest @ValueSource(strings = {"token-error", "malformed-token", "wrong-app", "profile-error", "transport-error", "jsonp"})
    void rejectsProtocolErrorsWithoutLeakingSecrets(String mode) {
        scenario.set(mode);
        runner().run(context -> {
            var filters = context.getBean(FilterChainProxy.class); var callback = startCallback(filters); var response = new MockHttpServletResponse();
            filters.doFilter(callback, response, new MockFilterChain()); assertThat(response.getStatus()).isEqualTo(401);
            assertThat(identity.get()).isNull(); assertThat(failure.get()).hasMessageNotContaining("private-access").hasMessageNotContaining("secret&+?");
        });
    }
    @Test void invalidStateDoesNotCallTokenEndpoint() {
        runner().run(context -> {
            var filters = context.getBean(FilterChainProxy.class); var callback = startCallback(filters); callback.setParameter("state", "forged");
            var response = new MockHttpServletResponse(); filters.doFilter(callback, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(401); assertThat(tokenCalls).hasValue(0);
        });
    }
    @Test void defaultsSupportMultipleRegistrationsAndDisabledMode() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(QqOAuth2AutoConfiguration.class, QuickOAuth2AutoConfiguration.class))
                .withPropertyValues("moli.oauth.client.enabled=true", "spring.security.oauth2.client.registration.one.provider=qq",
                        "spring.security.oauth2.client.registration.one.client-id=one", "spring.security.oauth2.client.registration.one.client-secret=s",
                        "spring.security.oauth2.client.registration.two.provider=qq", "spring.security.oauth2.client.registration.two.client-id=two",
                        "spring.security.oauth2.client.registration.two.client-secret=s").run(context -> {
                    assertThat(context).hasNotFailed(); var registrations = context.getBean(ClientRegistrationRepository.class);
                    assertThat(registrations.findByRegistrationId("two").getScopes()).containsExactly("get_user_info");
                    assertThat(registrations.findByRegistrationId("one").getProviderDetails().getTokenUri()).isEqualTo("https://graph.qq.com/oauth2.0/token");
                });
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(QqOAuth2AutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(QqOAuth2ProviderAdapter.class));
    }
}
