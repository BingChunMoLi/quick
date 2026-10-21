package com.bingchunmoli.security.autoconfigure;

import com.bingchunmoli.security.jwt.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityWebApplicationTest {
    private static ConfigurableApplicationContext context;
    private static String url;
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String SECRET = "security-web-regression-test-secret-change-me";

    @BeforeAll
    static void start() {
        context = new SpringApplicationBuilder(Host.class).run("--server.port=0", "--server.address=127.0.0.1",
                "--spring.main.banner-mode=off", "--logging.level.root=OFF", "--moli.security.jwt.secret=" + SECRET);
        url = "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    @AfterAll
    static void stop() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void defaultWebApplicationStartsAndAuthenticatesAValidToken() throws Exception {
        JwtToken token = context.getBean(JwtTokenService.class).createToken(user());
        var response = get(token.getToken());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("alice");
    }

    @Test
    void malformedTokenReturnsUnauthorizedOverHttp() throws Exception {
        assertThat(get("invalid-token").statusCode()).isEqualTo(401);
    }

    @Test
    void expiredTokenReturnsUnauthorizedOverHttp() throws Exception {
        JwtTokenProperties properties = new JwtTokenProperties();
        properties.setSecret(SECRET);
        properties.setMode(JwtMode.STATELESS);
        properties.setTimeout(Duration.ofSeconds(-60));
        try (var store = new InMemoryJwtSessionStore()) {
            JwtToken token = new JwtTokenService(properties, store).createToken(user());
            assertThat(get(token.getToken()).statusCode()).isEqualTo(401);
        }
    }

    @Test
    void revokedTokenReturnsUnauthorizedOverHttp() throws Exception {
        JwtTokenService tokens = context.getBean(JwtTokenService.class);
        JwtToken token = tokens.createToken(user());
        tokens.forceLogoutToken(token.getToken());
        assertThat(get(token.getToken()).statusCode()).isEqualTo(401);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.bingchunmoli.security.jwt.JwtClaimsFixtures#invalidClaims")
    void signedTokenWithInvalidClaimsReturnsUnauthorizedOverHttp(String label, Map<String, Object> claims) throws Exception {
        var response = get(JwtClaimsFixtures.sign(claims, SECRET));
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).doesNotContain("NullPointerException", "IllegalArgumentException", SECRET);
    }

    private HttpResponse<String> get(String token) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url + "/whoami"))
                        .header("Authorization", "Bearer " + token).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JwtUser user() {
        return new JwtUser("user", "alice", List.of("ROLE_USER"), Map.of());
    }

    // No @EnableWebSecurity and no Boot security starter: this is the documented minimal host.
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(Endpoint.class)
    static class Host {
        @Bean
        AuthenticationManager authenticationManager() {
            return authentication -> authentication;
        }
    }

    @RestController
    static class Endpoint {
        @GetMapping("/whoami")
        String whoami() {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        }
    }
}
