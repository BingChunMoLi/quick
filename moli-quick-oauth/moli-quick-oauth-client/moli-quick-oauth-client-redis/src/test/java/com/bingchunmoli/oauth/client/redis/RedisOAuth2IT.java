package com.bingchunmoli.oauth.client.redis;

import com.bingchunmoli.oauth.client.*;
import com.bingchunmoli.oauth.client.autoconfigure.*;
import com.bingchunmoli.oauth.client.web.*;
import java.time.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.*;
import org.springframework.security.oauth2.core.user.*;
import org.springframework.security.web.*;
import org.springframework.web.util.UriComponentsBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisOAuth2IT {
    static LettuceConnectionFactory factory;
    static StringRedisTemplate redis;
    static ClientRegistration registration;
    static ClientRegistrationRepository registrations;
    @BeforeAll static void connect() {
        int port = Integer.parseInt(Objects.requireNonNullElse(System.getenv("OAUTH_TEST_REDIS_PORT"), "16379"));
        factory = new LettuceConnectionFactory("127.0.0.1", port); factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory); redis.afterPropertiesSet();
        assertThat(redis.getConnectionFactory().getConnection().ping()).isEqualTo("PONG");
        registration = ClientRegistration.withRegistrationId("test").clientId("id").clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).authorizationUri("https://provider/authorize")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}").tokenUri("https://provider/token")
                .userInfoUri("https://provider/user").userNameAttributeName("id").build();
        registrations = new InMemoryClientRegistrationRepository(registration);
    }
    @AfterAll static void disconnect() { factory.destroy(); }

    @Test void stateIsBrowserBoundSingleUseAndSharedBetweenInstances() {
        var first = new RedisAuthorizationRequestRepository(redis, Duration.ofMinutes(1), true);
        var second = new RedisAuthorizationRequestRepository(redis, Duration.ofMinutes(1), true);
        String state = UUID.randomUUID().toString();
        var start = new MockHttpServletRequest(); var response = new MockHttpServletResponse();
        first.saveAuthorizationRequest(authorization(state), start, response);
        assertThat(start.getSession(false)).isNull();
        String header = response.getHeader("Set-Cookie");
        assertThat(header).contains("HttpOnly", "Secure", "SameSite=Lax");
        var callback = callback(state, header);
        var foreign = new MockHttpServletRequest(); foreign.setParameter("state", state);
        assertThat(second.removeAuthorizationRequest(foreign, new MockHttpServletResponse())).isNull();
        assertThat(second.loadAuthorizationRequest(callback).getAttribute("code_verifier").toString()).isEqualTo("verifier");
        assertThat(second.removeAuthorizationRequest(callback, new MockHttpServletResponse())).isNotNull();
        assertThat(first.removeAuthorizationRequest(callback, new MockHttpServletResponse())).isNull();
        assertThat(callback.getSession(false)).isNull();
    }
    @Test void stateExpiresAndConcurrentTabsKeepSeparateCookies() throws Exception {
        var repository = new RedisAuthorizationRequestRepository(redis, Duration.ofMillis(250), false);
        var start = new MockHttpServletRequest();
        var first = new MockHttpServletResponse(); var second = new MockHttpServletResponse();
        String one = UUID.randomUUID().toString(), two = UUID.randomUUID().toString();
        repository.saveAuthorizationRequest(authorization(one), start, first);
        repository.saveAuthorizationRequest(authorization(two), start, second);
        assertThat(first.getHeader("Set-Cookie").split("=")[0]).isNotEqualTo(second.getHeader("Set-Cookie").split("=")[0]);
        assertThat(repository.removeAuthorizationRequest(callback(one, first.getHeader("Set-Cookie")), new MockHttpServletResponse())).isNotNull();
        Thread.sleep(350);
        assertThat(repository.loadAuthorizationRequest(callback(two, second.getHeader("Set-Cookie")))).isNull();
    }
    @Test void tokenStorageSurvivesNewServiceAndPreservesUnknownExpiry() {
        String principal = UUID.randomUUID().toString();
        var first = new RedisOAuth2AuthorizedClientService(redis, registrations, null);
        var second = new RedisOAuth2AuthorizedClientService(redis, registrations, null);
        var ref = new AuthorizedClientRef("test", principal);
        try {
            var original = client(principal, null, new OAuth2RefreshToken("refresh", Instant.now()));
            first.saveAuthorizedClient(original, QuickOAuth2Client.principal(principal));
            OAuth2AuthorizedClient loaded = second.loadAuthorizedClient("test", principal);
            assertThat(loaded.getAccessToken().getExpiresAt()).isNull();
            assertThat(loaded.getRefreshToken().getExpiresAt()).isNull();
            assertThat(redis.getExpire(RedisOAuth2AuthorizedClientService.tokenKey(ref))).isEqualTo(-1);
            Instant expiration = Instant.now().plusSeconds(100);
            second.saveAuthorizedClient(client(principal, Instant.now().plusSeconds(10),
                    new OAuth2RefreshToken("rotated", Instant.now(), expiration)), QuickOAuth2Client.principal(principal));
            assertThat(redis.getExpire(RedisOAuth2AuthorizedClientService.tokenKey(ref))).isBetween(90L, 100L);
            assertThat(((OAuth2AuthorizedClient) first.loadAuthorizedClient("test", principal)).getRefreshToken().getExpiresAt()).isEqualTo(expiration);
        } finally { first.removeAuthorizedClient("test", principal); }
        assertThat((OAuth2AuthorizedClient) second.loadAuthorizedClient("test", principal)).isNull();
    }
    @Test void tokenTtlRetainsAccessAfterRefreshExpires() {
        String principal = UUID.randomUUID().toString();
        var service = new RedisOAuth2AuthorizedClientService(redis, registrations, null);
        var ref = new AuthorizedClientRef("test", principal);
        try {
            Instant now = Instant.now();
            service.saveAuthorizedClient(client(principal, now.plusSeconds(300),
                    new OAuth2RefreshToken("refresh", now.minusSeconds(100), now.plusSeconds(30))),
                    QuickOAuth2Client.principal(principal));
            assertThat(redis.getExpire(RedisOAuth2AuthorizedClientService.tokenKey(ref))).isBetween(290L, 300L);
            service.saveAuthorizedClient(client(principal, null,
                    new OAuth2RefreshToken("expired-refresh", now.minusSeconds(100), now.minusSeconds(1))),
                    QuickOAuth2Client.principal(principal));
            assertThat(redis.getExpire(RedisOAuth2AuthorizedClientService.tokenKey(ref))).isEqualTo(-1);
            assertThat(((OAuth2AuthorizedClient) service.loadAuthorizedClient("test", principal)).getAccessToken().getExpiresAt()).isNull();
        } finally { service.removeAuthorizedClient("test", principal); }
    }
    @Test void separateInstancesCoordinateRefreshAndRenewLease() throws Exception {
        String principal = UUID.randomUUID().toString();
        try (var first = new RedisRefreshCoordinator(redis, Duration.ofSeconds(4), Duration.ofMillis(600));
             var second = new RedisRefreshCoordinator(redis, Duration.ofSeconds(4), Duration.ofMillis(600))) {
            var serviceOne = new RedisOAuth2AuthorizedClientService(redis, registrations, first);
            var serviceTwo = new RedisOAuth2AuthorizedClientService(redis, registrations, second);
            serviceOne.saveAuthorizedClient(client(principal, Instant.now().minusSeconds(1), new OAuth2RefreshToken("refresh", Instant.now())),
                    QuickOAuth2Client.principal(principal));
            AtomicInteger calls = new AtomicInteger();
            OAuth2AuthorizedClientProvider provider = context -> {
                if (context.getAuthorizedClient().getAccessToken().getExpiresAt().isAfter(Instant.now())) return null;
                calls.incrementAndGet();
                try { Thread.sleep(900); } catch (InterruptedException ex) { throw new IllegalStateException(); }
                return client(principal, Instant.now().plusSeconds(100), context.getAuthorizedClient().getRefreshToken());
            };
            var managerOne = new CoordinatedAuthorizedClientManager(registrations, serviceOne, provider, first);
            var managerTwo = new CoordinatedAuthorizedClientManager(registrations, serviceTwo, provider, second);
            var request = OAuth2AuthorizeRequest.withClientRegistrationId("test").principal(QuickOAuth2Client.principal(principal)).build();
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                var a = pool.submit(() -> managerOne.authorize(request));
                var c = pool.submit(() -> managerTwo.authorize(request));
                assertThat(a.get(5, TimeUnit.SECONDS)).isNotNull();
                assertThat(c.get(5, TimeUnit.SECONDS)).isNotNull();
                assertThat(calls).hasValue(1);
            } finally { pool.shutdownNow(); serviceOne.removeAuthorizedClient("test", principal); }
        }
    }
    @Test void timeoutAndLostOwnershipCannotWriteOrReleaseAnotherOwnersLock() {
        var ref = new AuthorizedClientRef("test", UUID.randomUUID().toString());
        try (var first = new RedisRefreshCoordinator(redis, Duration.ofMillis(100), Duration.ofSeconds(10));
             var second = new RedisRefreshCoordinator(redis, Duration.ofMillis(100), Duration.ofSeconds(10))) {
            var service = new RedisOAuth2AuthorizedClientService(redis, registrations, first);
            try (var lease = first.acquire(ref)) {
                assertThatThrownBy(() -> second.acquire(ref)).hasMessageContaining("timed out");
                redis.opsForValue().set(RedisRefreshCoordinator.lockKey(ref), "replacement", Duration.ofSeconds(10));
                assertThatThrownBy(lease::checkOwned).hasMessageContaining("ownership lost");
                assertThatThrownBy(() -> service.saveAuthorizedClient(client(ref.principalName(), null, null),
                        QuickOAuth2Client.principal(ref.principalName()))).hasMessageContaining("ownership lost");
            }
            assertThat(redis.opsForValue().get(RedisRefreshCoordinator.lockKey(ref))).isEqualTo("replacement");
        } finally { redis.delete(RedisRefreshCoordinator.lockKey(ref)); }
    }
    @Test @SuppressWarnings("unchecked")
    void timedOutAndShutdownAcquisitionsLeaveNoRedisLock() {
        var ref = new AuthorizedClientRef("test", UUID.randomUUID().toString());
        var intercepted = spy(redis);
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        doReturn(operations).when(intercepted).opsForValue();
        var stopping = new AtomicReference<RedisRefreshCoordinator>();
        when(operations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(invocation -> {
            if (stopping.get() == null) Thread.sleep(100);
            Boolean acquired = redis.opsForValue().setIfAbsent(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2, Duration.class));
            if (stopping.get() != null) stopping.get().close();
            return acquired;
        });
        try (var slow = new RedisRefreshCoordinator(intercepted, Duration.ofMillis(30), Duration.ofSeconds(5));
             var stopped = new RedisRefreshCoordinator(intercepted, Duration.ofSeconds(2), Duration.ofSeconds(5));
             var observer = new RedisRefreshCoordinator(redis, Duration.ofSeconds(2), Duration.ofSeconds(5))) {
            assertThatThrownBy(() -> slow.acquire(ref)).hasMessageContaining("timed out");
            assertThat(slow.ownership()).isNull();
            assertThat(redis.hasKey(RedisRefreshCoordinator.lockKey(ref))).isFalse();
            stopping.set(stopped);
            assertThatThrownBy(() -> stopped.acquire(ref)).hasMessageContaining("closed");
            assertThat(stopped.ownership()).isNull();
            assertThat(redis.hasKey(RedisRefreshCoordinator.lockKey(ref))).isFalse();
            try (var lease = observer.acquire(ref)) { lease.checkOwned(); }
            assertThat(redis.hasKey(RedisRefreshCoordinator.lockKey(ref))).isFalse();
        } finally { redis.delete(RedisRefreshCoordinator.lockKey(ref)); }
    }
    @Test void differentApplicationNamespacesCannotReadEachOthersTokensOrLocks() {
        String principal = UUID.randomUUID().toString();
        var ref = new AuthorizedClientRef("test", principal);
        String prefix = "test:" + UUID.randomUUID();
        try (var one = new RedisRefreshCoordinator(redis, Duration.ofSeconds(1), Duration.ofSeconds(5), prefix + ":one");
             var two = new RedisRefreshCoordinator(redis, Duration.ofSeconds(1), Duration.ofSeconds(5), prefix + ":two")) {
            var first = new RedisOAuth2AuthorizedClientService(redis, registrations, one, prefix + ":one");
            var second = new RedisOAuth2AuthorizedClientService(redis, registrations, two, prefix + ":two");
            try {
                first.saveAuthorizedClient(client(principal, null, null), QuickOAuth2Client.principal(principal));
                assertThat((OAuth2AuthorizedClient) second.loadAuthorizedClient("test", principal)).isNull();
                try (var a = one.acquire(ref); var c = two.acquire(ref)) { a.checkOwned(); c.checkOwned(); }
            } finally { first.removeAuthorizedClient("test", principal); }
        }
    }

    @Configuration(proxyBeanMethods = false) @EnableWebSecurity
    static class Host {
        @Bean StringRedisTemplate template() { return redis; }
        @Bean ClientRegistrationRepository registrations() { return registrations; }
        @Bean OAuth2ProviderAdapter provider() {
            return new OAuth2ProviderAdapter() {
                @Override public String id() { return "test"; }
                @Override public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> authorizationCodeClient() {
                    return request -> OAuth2AccessTokenResponse.withToken("access").tokenType(OAuth2AccessToken.TokenType.BEARER).expiresIn(3600).build();
                }
                @Override public org.springframework.security.oauth2.client.userinfo.OAuth2UserService<
                        org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest, OAuth2User> userService() {
                    return request -> new DefaultOAuth2User(List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("USER")),
                            Map.of("id", "external"), "id");
                }
            };
        }
        @Bean OAuth2LoginHandler handler() {
            return (identity, user, ref, request, response) -> {
                assertThat(request.getSession(false)).isNull(); response.setStatus(204);
            };
        }
        @Bean SecurityFilterChain chain(HttpSecurity http, QuickOAuth2WebSupport support) {
            http.sessionManagement(session -> session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS));
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
            http.with(support.configurer(), Customizer.withDefaults());
            return http.build();
        }
    }
    @ParameterizedTest @ValueSource(strings = {"memory", "REDIS"})
    void actualSecurityChainKeepsSuccessAndErrorPathsSessionless(String authorizedClientStore) {
        new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                RedisOAuth2AutoConfiguration.class, RedisOAuth2WebAutoConfiguration.class,
                QuickOAuth2AutoConfiguration.class, QuickOAuth2WebAutoConfiguration.class))
                .withUserConfiguration(Host.class).withPropertyValues("moli.oauth.client.enabled=true",
                        "moli.oauth.client.login.enabled=true", "moli.oauth.client.authorization-state.store=REDIS",
                        "moli.oauth.client.authorized-client.store=" + authorizedClientStore,
                        "moli.oauth.client.refresh.coordination=AUTO",
                        "moli.oauth.client.authorization-state.cookie-secure=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    if ("redis".equalsIgnoreCase(authorizedClientStore)) {
                        assertThat(context).hasSingleBean(RedisRefreshCoordinator.class).hasSingleBean(RedisOAuth2AuthorizedClientService.class);
                    }
                    try {
                        var filters = context.getBean(FilterChainProxy.class);
                        var start = request("/oauth2/authorization/test"); var redirect = new MockHttpServletResponse();
                        filters.doFilter(start, redirect, new MockFilterChain());
                        assertThat(start.getSession(false)).isNull();
                        String state = URLDecoder.decode(UriComponentsBuilder.fromUriString(redirect.getRedirectedUrl()).build()
                                .getQueryParams().getFirst("state"), StandardCharsets.UTF_8);
                        var callback = callback(state, redirect.getHeader("Set-Cookie"));
                        callback.setRequestURI("/login/oauth2/code/test"); callback.setServletPath("/login/oauth2/code/test");
                        callback.setParameter("code", "code");
                        var response = new MockHttpServletResponse();
                        filters.doFilter(callback, response, new MockFilterChain());
                        assertThat(response.getStatus()).isEqualTo(204);
                        assertThat(callback.getSession(false)).isNull();
                        var denied = request("/login/oauth2/code/test");
                        denied.setParameter("state", state); denied.setParameter("code", "code");
                        var error = new MockHttpServletResponse();
                        filters.doFilter(denied, error, new MockFilterChain());
                        assertThat(error.getStatus()).isEqualTo(401);
                        assertThat(denied.getSession(false)).isNull();
                    } finally {
                        context.getBean(QuickOAuth2Client.class).remove(new AuthorizedClientRef("test", "external"));
                    }
                });
    }
    static MockHttpServletRequest request(String path) {
        var request = new MockHttpServletRequest("GET", path); request.setServletPath(path); return request;
    }
    static MockHttpServletRequest callback(String state, String header) {
        var request = request("/callback"); request.setParameter("state", state);
        String pair = header.split(";")[0]; int split = pair.indexOf('=');
        request.setCookies(new Cookie(pair.substring(0, split), pair.substring(split + 1)));
        return request;
    }
    static OAuth2AuthorizationRequest authorization(String state) {
        return OAuth2AuthorizationRequest.authorizationCode().authorizationUri("https://provider/authorize")
                .clientId("id").state(state).redirectUri("https://host/callback")
                .attributes(a -> a.put("code_verifier", "verifier")).build();
    }
    static OAuth2AuthorizedClient client(String principal, Instant expires, OAuth2RefreshToken refresh) {
        return new OAuth2AuthorizedClient(registration, principal,
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access", Instant.now().minusSeconds(10), expires), refresh);
    }
}
