package com.bingchunmoli.oauth.client.microsoft;

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
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.*;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class MicrosoftLoginTest {
    static final String TENANT = "11111111-2222-3333-4444-555555555555";
    static HttpServer server;
    static String base;
    static RSAKey key, wrongKey;
    static final AtomicReference<String> mode = new AtomicReference<>(), nonce = new AtomicReference<>(), tokenForm = new AtomicReference<>();
    static final AtomicReference<OAuth2Identity> identity = new AtomicReference<>();
    static final AtomicReference<OidcUser> nativeUser = new AtomicReference<>();
    @BeforeAll static void start() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("key").generate(); wrongKey = new RSAKeyGenerator(2048).keyID("key").generate();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/jwks", exchange -> {
            Map<String, Object> publicKey = new LinkedHashMap<>(key.toPublicJWK().toJSONObject());
            if (!"missing-key-issuer".equals(mode.get())) publicKey.put("issuer", "key-issuer".equals(mode.get())
                    ? MicrosoftOAuth2ProviderAdapter.AUTHORITY + "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/v2.0"
                    : MicrosoftOAuth2ProviderAdapter.AUTHORITY + "{tenantid}/v2.0");
            json(exchange, Map.of("keys", List.of(publicKey)));
        });
        server.createContext("/token", exchange -> {
            tokenForm.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                String scenario = mode.get(), tenant = tenant(), issuer = MicrosoftOAuth2ProviderAdapter.AUTHORITY + tenant + "/v2.0";
                var claims = new JWTClaimsSet.Builder().issuer("issuer".equals(scenario) ? "https://untrusted.test/" + tenant : issuer)
                        .subject("stable-subject").audience("audience".equals(scenario) ? "wrong-app" : "app")
                        .issueTime(Date.from(Instant.now().minusSeconds(120)))
                        .expirationTime(Date.from("expired".equals(scenario) ? Instant.now().minusSeconds(120) : Instant.now().plusSeconds(300)))
                        .claim("tid", "tid".equals(scenario) ? "invalid-directory" : tenant)
                        .claim("nonce", "nonce".equals(scenario) ? "wrong-nonce" : nonce.get())
                        .claim("name", "Microsoft User").claim("email", "user@example.test").build();
                var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("key").build(), claims);
                jwt.sign(new RSASSASigner("signature".equals(scenario) ? wrongKey : key));
                json(exchange, Map.of("access_token", "private-access", "token_type", "Bearer", "expires_in", 3600, "id_token", jwt.serialize()));
            } catch (JOSEException error) { throw new java.io.IOException(error); }
        });
        server.createContext("/user", exchange -> json(exchange, Map.of("sub", "stable-subject")));
        server.start();
    }
    @AfterAll static void stop() { server.stop(0); }
    @BeforeEach void reset() { mode.set("ok"); identity.set(null); nativeUser.set(null); }
    static String tenant() { return "personal".equals(mode.get()) ? MicrosoftOAuth2ProviderAdapter.CONSUMER_TENANT : TENANT; }
    static void json(HttpExchange exchange, Object value) throws java.io.IOException {
        byte[] data = JsonMapper.builder().build().writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, data.length); exchange.getResponseBody().write(data); exchange.close();
    }
    @Configuration(proxyBeanMethods = false)
    static class Registrations {
        @Bean ClientRegistrationRepository registrations() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("work")
                    .clientId("app").clientSecret("secret").clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "profile", "email").authorizationUri(base + "/authorize").tokenUri(base + "/token")
                    .userInfoUri(base + "/user").userNameAttributeName("sub").jwkSetUri(base + "/jwks")
                    .issuerUri(MicrosoftOAuth2ProviderAdapter.AUTHORITY + "{tenantid}/v2.0").build());
        }
    }
    @Configuration(proxyBeanMethods = false) @EnableWebSecurity
    static class Host {
        @Bean SecurityFilterChain chain(HttpSecurity http, QuickOAuth2WebSupport support) {
            http.authorizeHttpRequests(r -> r.anyRequest().permitAll());
            http.with(support.configurer(), Customizer.withDefaults()); return http.build();
        }
        @Bean OAuth2LoginHandler handler() {
            return (value, user, ref, request, response) -> { identity.set(value); nativeUser.set((OidcUser) user); response.setStatus(204); };
        }
    }
    static WebApplicationContextRunner runner(String... properties) {
        return new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(MicrosoftOAuth2AutoConfiguration.class,
                QuickOAuth2AutoConfiguration.class, QuickOAuth2WebAutoConfiguration.class)).withUserConfiguration(Registrations.class, Host.class)
                .withPropertyValues("moli.oauth.client.enabled=true", "moli.oauth.client.login.enabled=true",
                        "moli.oauth.client.provider-mappings.work=microsoft").withPropertyValues(properties);
    }
    static MockHttpServletRequest request(String path) { var r = new MockHttpServletRequest("GET", path); r.setServletPath(path); return r; }
    static MockHttpServletResponse login(FilterChainProxy filters) throws Exception {
        var start = request("/oauth2/authorization/work"); var redirect = new MockHttpServletResponse();
        filters.doFilter(start, redirect, new MockFilterChain());
        var params = UriComponentsBuilder.fromUriString(redirect.getRedirectedUrl()).build().getQueryParams();
        assertThat(params.getFirst("code_challenge_method")).isEqualTo("S256");
        nonce.set(URLDecoder.decode(params.getFirst("nonce"), StandardCharsets.UTF_8));
        var callback = request("/login/oauth2/code/work"); callback.setSession(start.getSession(false));
        callback.setParameter("state", URLDecoder.decode(params.getFirst("state"), StandardCharsets.UTF_8)); callback.setParameter("code", "code");
        var response = new MockHttpServletResponse(); filters.doFilter(callback, response, new MockFilterChain()); return response;
    }
    @ParameterizedTest @ValueSource(strings = {"ok", "personal"})
    void commonAcceptsVerifiedOrganizationalAndPersonalIdentities(String scenario) {
        mode.set(scenario);
        runner().run(context -> {
            assertThat(login(context.getBean(FilterChainProxy.class)).getStatus()).isEqualTo(204);
            String subject = MicrosoftOAuth2ProviderAdapter.AUTHORITY + tenant() + "/v2.0|stable-subject";
            assertThat(identity.get().provider()).isEqualTo("microsoft"); assertThat(identity.get().subject()).isEqualTo(subject);
            assertThat(nativeUser.get().getName()).isEqualTo(subject); assertThat(nativeUser.get().getIdToken()).isNotNull();
            assertThat(tokenForm.get()).contains("code_verifier=", "client_secret=secret");
            var service = context.getBean(OAuth2AuthorizedClientService.class);
            assertThat((Object) service.loadAuthorizedClient("work", subject)).isNotNull();
        });
    }
    @ParameterizedTest @ValueSource(strings = {"issuer", "tid", "audience", "nonce", "expired", "signature", "key-issuer", "missing-key-issuer"})
    void rejectsInvalidIdTokensOrSigningKeyScope(String scenario) {
        mode.set(scenario); runner().run(context -> {
            assertThat(login(context.getBean(FilterChainProxy.class)).getStatus()).isEqualTo(401); assertThat(identity.get()).isNull();
        });
    }
    @Test void organizationAudienceRejectsPersonalAccounts() {
        mode.set("personal"); runner("moli.oauth.client.microsoft.tenant=organizations").run(context ->
                assertThat(login(context.getBean(FilterChainProxy.class)).getStatus()).isEqualTo(401));
    }
    @Test void consumerAudienceRejectsOrganizationalAccounts() {
        runner("moli.oauth.client.microsoft.tenant=consumers").run(context ->
                assertThat(login(context.getBean(FilterChainProxy.class)).getStatus()).isEqualTo(401));
    }
    @Test void tenantAllowlistIsEnforced() {
        runner("moli.oauth.client.microsoft.allowed-tenants[0]=aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee").run(context ->
                assertThat(login(context.getBean(FilterChainProxy.class)).getStatus()).isEqualTo(401));
    }
    @Test void selectedDirectoryAcceptsItsOwnIssuer() {
        runner("moli.oauth.client.microsoft.tenant=" + TENANT).run(context ->
                assertThat(login(context.getBean(FilterChainProxy.class)).getStatus()).isEqualTo(204));
    }
    @Test void equalSubjectsFromDifferentDirectoriesHaveSeparateAuthorizations() {
        runner().run(context -> {
            var filters = context.getBean(FilterChainProxy.class);
            assertThat(login(filters).getStatus()).isEqualTo(204); String first = nativeUser.get().getName();
            mode.set("personal"); assertThat(login(filters).getStatus()).isEqualTo(204); String second = nativeUser.get().getName();
            assertThat(second).isNotEqualTo(first);
            var service = context.getBean(OAuth2AuthorizedClientService.class);
            assertThat((Object) service.loadAuthorizedClient("work", first)).isNotNull();
            assertThat((Object) service.loadAuthorizedClient("work", second)).isNotNull();
        });
    }
    @Test void defaultsDoNotRequireDiscoveryAndNativeOverridesAreRetained() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(MicrosoftOAuth2AutoConfiguration.class, QuickOAuth2AutoConfiguration.class))
                .withPropertyValues("moli.oauth.client.enabled=true", "moli.oauth.client.microsoft.offline=true",
                        "spring.security.oauth2.client.registration.microsoft.client-id=app",
                        "spring.security.oauth2.client.registration.microsoft.client-secret=secret").run(context -> {
                    var registration = context.getBean(ClientRegistrationRepository.class).findByRegistrationId("microsoft");
                    assertThat(registration.getProviderDetails().getIssuerUri()).isEqualTo(MicrosoftOAuth2ProviderAdapter.AUTHORITY + "{tenantid}/v2.0");
                    assertThat(registration.getScopes()).contains("openid", "offline_access");
                    assertThat(registration.getProviderDetails().getAuthorizationUri()).endsWith("/common/oauth2/v2.0/authorize");
                });
    }
}
