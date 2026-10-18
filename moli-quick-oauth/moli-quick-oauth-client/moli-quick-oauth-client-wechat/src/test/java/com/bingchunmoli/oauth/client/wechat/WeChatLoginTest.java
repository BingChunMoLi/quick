package com.bingchunmoli.oauth.client.wechat;

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
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.util.*;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class WeChatLoginTest {
    static HttpServer server;
    static String base;
    static final AtomicReference<OAuth2Identity> identity = new AtomicReference<>();
    static final AtomicReference<Exception> failure = new AtomicReference<>();
    static final AtomicReference<MultiValueMap<String, String>> tokenQuery = new AtomicReference<>(), refreshQuery = new AtomicReference<>();
    static final AtomicReference<String> scope = new AtomicReference<>(), scenario = new AtomicReference<>("ok");
    static final AtomicInteger userCalls = new AtomicInteger();
    @BeforeAll static void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/token", exchange -> {
            tokenQuery.set(params(base + exchange.getRequestURI()));
            json(exchange, scenario.get().equals("token-error") ? Map.of("errcode", 40029, "errmsg", "secret&+?") :
                    Map.of("access_token", "private-access", "expires_in", 7200, "refresh_token", "refresh", "openid", "open-id", "scope", scope.get()));
        });
        server.createContext("/refresh", exchange -> {
            refreshQuery.set(params(base + exchange.getRequestURI()));
            json(exchange, scenario.get().equals("refresh-error") ? Map.of("errcode", 40030, "errmsg", "private-refresh") :
                    Map.of("access_token", "new-access", "expires_in", 7200, "refresh_token", "rotated", "openid", "open-id", "scope", scope.get()));
        });
        server.createContext("/user", exchange -> {
            userCalls.incrementAndGet();
            json(exchange, scenario.get().equals("profile-error") ? Map.of("errcode", 40003, "errmsg", "private-access") :
                    Map.of("openid", scenario.get().equals("wrong-user") ? "another-user" : "open-id", "nickname", "微信用户",
                            "headimgurl", "https://example.test/avatar", "unionid", "union-id"));
        });
        server.start();
    }
    @AfterAll static void stop() { server.stop(0); }
    @BeforeEach void reset() { scenario.set("ok"); identity.set(null); failure.set(null); scope.set("snsapi_login"); userCalls.set(0); }
    static void json(HttpExchange exchange, Object data) throws java.io.IOException {
        byte[] bytes = JsonMapper.builder().build().writeValueAsBytes(data);
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
    }
    static MultiValueMap<String, String> params(String uri) {
        var decoded = new LinkedMultiValueMap<String, String>();
        UriComponentsBuilder.fromUriString(uri).build().getQueryParams().forEach((key, values) ->
                values.forEach(value -> decoded.add(key, URLDecoder.decode(value, StandardCharsets.UTF_8)))); return decoded;
    }
    @Configuration(proxyBeanMethods = false) @EnableWebSecurity
    static class Host {
        @Bean SecurityFilterChain chain(HttpSecurity http, QuickOAuth2WebSupport support) {
            http.authorizeHttpRequests(r -> r.anyRequest().permitAll()); http.with(support.configurer(), Customizer.withDefaults()); return http.build();
        }
        @Bean OAuth2LoginHandler handler() { return (value, user, ref, request, response) -> { identity.set(value); response.setStatus(204); }; }
        @Bean(name = "quickOAuth2FailureHandler") org.springframework.security.web.authentication.AuthenticationFailureHandler failureHandler() {
            return (request, response, error) -> { failure.set(error); response.sendError(401); };
        }
    }
    static WebApplicationContextRunner runner(String channel, String requestedScope) {
        scope.set(requestedScope);
        return new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(WeChatOAuth2AutoConfiguration.class,
                QuickOAuth2AutoConfiguration.class, QuickOAuth2WebAutoConfiguration.class)).withUserConfiguration(Host.class)
                .withPropertyValues("moli.oauth.client.enabled=true", "moli.oauth.client.login.enabled=true",
                        "moli.oauth.client.wechat.refresh-token-uri=" + base + "/refresh",
                        "spring.security.oauth2.client.registration.work.provider=" + channel,
                        "spring.security.oauth2.client.registration.work.client-id=app",
                        "spring.security.oauth2.client.registration.work.client-secret=secret&+?",
                        "spring.security.oauth2.client.registration.work.scope=" + requestedScope,
                        "spring.security.oauth2.client.provider." + channel + ".authorization-uri=" + base + "/authorize",
                        "spring.security.oauth2.client.provider." + channel + ".token-uri=" + base + "/token",
                        "spring.security.oauth2.client.provider." + channel + ".user-info-uri=" + base + "/user");
    }
    static MockHttpServletRequest request(String path) { var request = new MockHttpServletRequest("GET", path); request.setServletPath(path); return request; }
    static MockHttpServletRequest callback(FilterChainProxy filters) throws Exception {
        var start = request("/oauth2/authorization/work"); var redirect = new MockHttpServletResponse(); filters.doFilter(start, redirect, new MockFilterChain());
        assertThat(redirect.getRedirectedUrl()).endsWith("#wechat_redirect");
        var query = params(redirect.getRedirectedUrl()); assertThat(query.keySet()).containsExactly("appid", "redirect_uri", "response_type", "scope", "state");
        assertThat(query.getFirst("appid")).isEqualTo("app"); assertThat(query.getFirst("scope")).isEqualTo(scope.get());
        assertThat(query.getFirst("state")).matches("[0-9a-f]{64}");
        var callback = request("/login/oauth2/code/work"); callback.setSession(start.getSession(false));
        callback.setParameter("state", query.getFirst("state")); callback.setParameter("code", "code&+?"); return callback;
    }
    @ParameterizedTest @CsvSource({"wechat,snsapi_login", "wechat-mp,snsapi_userinfo", "wechat-mp,snsapi_base"})
    void websiteAndOfficialAccountLoginAndRefresh(String channel, String requestedScope) {
        runner(channel, requestedScope).run(context -> {
            assertThat(context).hasNotFailed(); var filters = context.getBean(FilterChainProxy.class); var callback = callback(filters);
            var response = new MockHttpServletResponse(); filters.doFilter(callback, response, new MockFilterChain());
            assertThat(failure.get()).isNull(); assertThat(response.getStatus()).isEqualTo(204);
            assertThat(identity.get().provider()).isEqualTo("wechat"); assertThat(identity.get().subject()).isEqualTo("app|open-id");
            assertThat(identity.get().attributes()).doesNotContainKeys("access_token", "refresh_token", "secret");
            assertThat(userCalls).hasValue(requestedScope.equals("snsapi_base") ? 0 : 1);
            assertThat(identity.get().name()).isEqualTo(requestedScope.equals("snsapi_base") ? null : "微信用户");
            assertThat(tokenQuery.get().getFirst("appid")).isEqualTo("app"); assertThat(tokenQuery.get().getFirst("secret")).isEqualTo("secret&+?");
            assertThat(tokenQuery.get().getFirst("code")).isEqualTo("code&+?"); assertThat(tokenQuery.get()).doesNotContainKey("client_secret");
            assertThat(callback.getSession(false).getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
            var service = context.getBean(OAuth2AuthorizedClientService.class); OAuth2AuthorizedClient saved = service.loadAuthorizedClient("work", "app|open-id");
            expire(service, saved); var quick = context.getBean(QuickOAuth2Client.class); var ref = new AuthorizedClientRef("work", "app|open-id");
            var refreshed = quick.authorize(ref); assertThat(refreshed.getAccessToken().getTokenValue()).isEqualTo("new-access");
            assertThat(refreshQuery.get().getFirst("appid")).isEqualTo("app"); assertThat(refreshQuery.get()).doesNotContainKey("secret");
            expire(service, refreshed); scenario.set("refresh-error");
            assertThatThrownBy(() -> quick.authorize(ref)).isInstanceOf(AuthorizationRequiredException.class).hasMessageNotContaining("private-refresh");
            assertThat((OAuth2AuthorizedClient) service.loadAuthorizedClient("work", "app|open-id")).isNull();
        });
    }
    static void expire(OAuth2AuthorizedClientService service, OAuth2AuthorizedClient client) {
        service.saveAuthorizedClient(new OAuth2AuthorizedClient(client.getClientRegistration(), client.getPrincipalName(),
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "expired", Instant.now().minusSeconds(120), Instant.now().minusSeconds(60),
                        client.getAccessToken().getScopes()), client.getRefreshToken()), QuickOAuth2Client.principal(client.getPrincipalName()));
    }
    @ParameterizedTest @ValueSource(strings = {"token-error", "profile-error", "wrong-user"})
    void rejectsProviderErrorsAndIdentityMismatch(String mode) {
        scenario.set(mode); runner("wechat", "snsapi_login").run(context -> {
            var filters = context.getBean(FilterChainProxy.class); var callback = callback(filters); var response = new MockHttpServletResponse();
            filters.doFilter(callback, response, new MockFilterChain()); assertThat(response.getStatus()).isEqualTo(401);
            assertThat(identity.get()).isNull(); assertThat(failure.get()).hasMessageNotContaining("private-access").hasMessageNotContaining("secret&+?");
        });
    }
    @Test void invalidScopesFailAtStartupAndDefaultModesAreSeparate() {
        runner("wechat", "snsapi_userinfo").run(context -> assertThat(context).hasFailed());
        runner("wechat-mp", "snsapi_base,snsapi_userinfo").run(context -> assertThat(context).hasFailed());
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(WeChatOAuth2AutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(WeChatOAuth2ProviderAdapter.class));
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(WeChatOAuth2AutoConfiguration.class, QuickOAuth2AutoConfiguration.class))
                .withPropertyValues("moli.oauth.client.enabled=true", "spring.security.oauth2.client.registration.web.provider=wechat",
                        "spring.security.oauth2.client.registration.web.client-id=web", "spring.security.oauth2.client.registration.web.client-secret=s",
                        "spring.security.oauth2.client.registration.mp.provider=wechat-mp", "spring.security.oauth2.client.registration.mp.client-id=mp",
                        "spring.security.oauth2.client.registration.mp.client-secret=s").run(context -> {
                    assertThat(context).hasNotFailed(); var registrations = context.getBean(ClientRegistrationRepository.class);
                    assertThat(registrations.findByRegistrationId("web").getScopes()).containsExactly("snsapi_login");
                    assertThat(registrations.findByRegistrationId("mp").getScopes()).containsExactly("snsapi_userinfo");
                });
    }
}
