package com.bingchunmoli.oauth.client.github;

import com.bingchunmoli.oauth.client.*;
import com.bingchunmoli.oauth.client.autoconfigure.*;
import com.bingchunmoli.oauth.client.web.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.*;
import org.springframework.security.oauth2.client.*;
import org.springframework.web.util.UriComponentsBuilder;
import static org.assertj.core.api.Assertions.*;

class GitHubLoginTest {
    static HttpServer server;
    static String base;
    static final AtomicReference<String> tokenRequest = new AtomicReference<>();
    static final AtomicInteger callbacks = new AtomicInteger();
    static final AtomicReference<OAuth2Identity> identity = new AtomicReference<>();
    static final AtomicReference<Exception> failure = new AtomicReference<>();
    @BeforeAll static void startProvider() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/token", exchange -> {
            String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            tokenRequest.set(form);
            String data = form.contains("code=long") ? "{\"access_token\":\"access\",\"token_type\":\"bearer\",\"scope\":\"read:user\"}" :
                    "{\"access_token\":\"access\",\"token_type\":\"bearer\",\"scope\":\"read:user\",\"expires_in\":3600,\"refresh_token\":\"refresh\",\"refresh_token_expires_in\":7200}";
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.createContext("/user", exchange -> {
            byte[] bytes = "{\"id\":42,\"login\":\"octocat\",\"name\":\"章鱼\",\"avatar_url\":\"https://example/avatar\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
    }
    @AfterAll static void stopProvider() { server.stop(0); }
    @BeforeEach void resetFailure() { failure.set(null); }
    WebApplicationContextRunner runner() {
        return new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                GitHubOAuth2AutoConfiguration.class, QuickOAuth2AutoConfiguration.class, QuickOAuth2WebAutoConfiguration.class))
                .withUserConfiguration(Host.class).withPropertyValues(
                        "moli.oauth.client.enabled=true", "moli.oauth.client.login.enabled=true",
                        "spring.security.oauth2.client.registration.work.provider=github",
                        "spring.security.oauth2.client.registration.work.client-id=id",
                        "spring.security.oauth2.client.registration.work.client-secret=secret",
                        "spring.security.oauth2.client.provider.github.authorization-uri=" + base + "/authorize",
                        "spring.security.oauth2.client.provider.github.token-uri=" + base + "/token",
                        "spring.security.oauth2.client.provider.github.user-info-uri=" + base + "/user");
    }
    @Configuration(proxyBeanMethods = false) @EnableWebSecurity
    static class Host {
        @Bean(name = "quickOAuth2FailureHandler")
        org.springframework.security.web.authentication.AuthenticationFailureHandler failureHandler() {
            return (request, response, exception) -> { failure.set(exception); response.sendError(401, "OAuth2 authorization failed"); };
        }
        @Bean SecurityFilterChain chain(HttpSecurity http, QuickOAuth2WebSupport support) {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
            http.with(support.configurer(), Customizer.withDefaults());
            return http.build();
        }
        @Bean OAuth2LoginHandler handler() {
            return (user, nativeUser, ref, request, response) -> {
                callbacks.incrementAndGet(); identity.set(user);
                assertThat(ref.principalName()).isEqualTo("42");
                response.setStatus(204);
            };
        }
    }
    @Test void callbackUsesPkceAndDoesNotPersistProviderAsBusinessLogin() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            var filters = context.getBean(FilterChainProxy.class);
            var start = request("/oauth2/authorization/work");
            var redirect = new MockHttpServletResponse();
            filters.doFilter(start, redirect, new MockFilterChain());
            assertThat(redirect.getStatus()).isEqualTo(302);
            var query = UriComponentsBuilder.fromUriString(redirect.getRedirectedUrl()).build().getQueryParams();
            assertThat(query.getFirst("code_challenge_method")).isEqualTo("S256");
            assertThat(query.getFirst("scope")).contains("read:user");
            assertThat(redirect.getRedirectedUrl()).startsWith(base + "/authorize");
            var callback = request("/login/oauth2/code/work");
            callback.setSession(start.getSession(false));
            callback.setParameter("state", URLDecoder.decode(query.getFirst("state"), StandardCharsets.UTF_8)); callback.setParameter("code", "ok");
            var response = new MockHttpServletResponse();
            filters.doFilter(callback, response, new MockFilterChain());
            assertThat(failure.getAndSet(null)).isNull();
            assertThat(response.getStatus()).isEqualTo(204);
            assertThat(tokenRequest.get()).contains("code_verifier=");
            assertThat(identity.get().subject()).isEqualTo("42");
            assertThat(identity.get().name()).isEqualTo("章鱼");
            assertThat(callback.getSession(false).getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
            var service = context.getBean(OAuth2AuthorizedClientService.class);
            OAuth2AuthorizedClient saved = service.loadAuthorizedClient("work", "42");
            assertThat(saved.getRefreshToken().getExpiresAt()).isAfter(java.time.Instant.now().plusSeconds(7000));
            var replay = request("/login/oauth2/code/work");
            replay.setSession(start.getSession(false)); replay.setParameter("state", URLDecoder.decode(query.getFirst("state"), StandardCharsets.UTF_8)); replay.setParameter("code", "ok");
            var denied = new MockHttpServletResponse();
            filters.doFilter(replay, denied, new MockFilterChain());
            assertThat(denied.getStatus()).isEqualTo(401);
            assertThat(denied.getErrorMessage()).doesNotContain("secret", "refresh");
        });
    }
    @Test void legacyGithubTokenRemainsNonExpiringAfterStorage() {
        runner().run(context -> {
            var filters = context.getBean(FilterChainProxy.class);
            var start = request("/oauth2/authorization/work"); var redirect = new MockHttpServletResponse();
            filters.doFilter(start, redirect, new MockFilterChain());
            String state = UriComponentsBuilder.fromUriString(redirect.getRedirectedUrl()).build().getQueryParams().getFirst("state");
            var callback = request("/login/oauth2/code/work"); callback.setSession(start.getSession(false));
            callback.setParameter("state", URLDecoder.decode(state, StandardCharsets.UTF_8)); callback.setParameter("code", "long");
            var response = new MockHttpServletResponse(); filters.doFilter(callback, response, new MockFilterChain());
            assertThat(failure.getAndSet(null)).isNull();
            assertThat(response.getStatus()).isEqualTo(204);
            OAuth2AuthorizedClient saved = context.getBean(OAuth2AuthorizedClientService.class).loadAuthorizedClient("work", "42");
            assertThat(saved.getAccessToken().getExpiresAt()).isNull();
            assertThat(context.getBean(QuickOAuth2Client.class).authorize(new AuthorizedClientRef("work", "42")).getAccessToken().getExpiresAt()).isNull();
        });
    }
    @Test void unsupportedGithubGrantFailsAtStartup() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                GitHubOAuth2AutoConfiguration.class, QuickOAuth2AutoConfiguration.class)).withPropertyValues(
                "moli.oauth.client.enabled=true", "spring.security.oauth2.client.registration.app.provider=github",
                "spring.security.oauth2.client.registration.app.client-id=id",
                "spring.security.oauth2.client.registration.app.authorization-grant-type=client_credentials")
                .run(context -> assertThat(context).hasFailed());
    }
    @Test void existingBusinessContextSurvivesAndSecurityUtilDefaultChainBacksOff() {
        runner().withConfiguration(AutoConfigurations.of(com.bingchunmoli.security.autoconfigure.SecurityUtilAutoConfiguration.class))
                .withPropertyValues("moli.security.jwt.enabled=false").run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean("moliSecurityFilterChain");
                    var filters = context.getBean(FilterChainProxy.class);
                    var business = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
                    business.setAuthentication(QuickOAuth2Client.principal("local-business-user"));
                    var session = new MockHttpSession();
                    session.setAttribute("SPRING_SECURITY_CONTEXT", business);
                    var start = request("/oauth2/authorization/work"); start.setSession(session);
                    var redirect = new MockHttpServletResponse();
                    filters.doFilter(start, redirect, new MockFilterChain());
                    String state = UriComponentsBuilder.fromUriString(redirect.getRedirectedUrl()).build().getQueryParams().getFirst("state");
                    var callback = request("/login/oauth2/code/work"); callback.setSession(session);
                    callback.setParameter("state", URLDecoder.decode(state, StandardCharsets.UTF_8)); callback.setParameter("code", "ok");
                    var response = new MockHttpServletResponse(); filters.doFilter(callback, response, new MockFilterChain());
                    assertThat(response.getStatus()).isEqualTo(204);
                    assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT")).isSameAs(business);
                    assertThat(business.getAuthentication().getName()).isEqualTo("local-business-user");
                });
    }
    static MockHttpServletRequest request(String path) {
        var request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path); request.setServerName("localhost"); request.setServerPort(80);
        return request;
    }
}
