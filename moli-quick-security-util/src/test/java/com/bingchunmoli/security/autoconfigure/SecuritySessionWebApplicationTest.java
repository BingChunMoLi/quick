package com.bingchunmoli.security.autoconfigure;

import com.bingchunmoli.security.logout.SecurityLogoutService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.*;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class SecuritySessionWebApplicationTest {
    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void loginIsRegisteredBeforeAnyBusinessRequestAndCanBeImmediatelyRevoked(boolean customChain, boolean bootSecurity) throws Exception {
        try (var context = start(customChain, true, false, true, bootSecurity)) {
            String url = url(context);
            CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
            assertThat(login(http, url).statusCode()).isEqualTo(302);
            assertThat(context.getBean(SessionRegistry.class).getAllPrincipals()).hasSize(1);
            assertThat(context.getBean(SecurityLogoutService.class).forceLogoutByUsername("user")).isEqualTo(1);
            assertThat(get(http, url, null).statusCode()).isEqualTo(401);
        }
    }

    @Test
    void basicFlagEnablesAuthenticationAndRejectsBadCredentials() throws Exception {
        try (var context = start(false, false, true, true)) {
            String url = url(context);
            HttpClient http = HttpClient.newHttpClient();
            assertThat(get(http, url, "user:password").statusCode()).isEqualTo(200);
            var invalid = get(http, url, "user:wrong");
            assertThat(invalid.statusCode()).isEqualTo(401);
            assertThat(invalid.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(value ->
                    assertThat(value).contains("Basic"));
        }
    }

    @Test
    void defaultDisabledFlagsDoNotEnablePasswordAuthentication() throws Exception {
        try (var context = start(false, false, false, true)) {
            HttpClient http = HttpClient.newHttpClient();
            assertThat(get(http, url(context), "user:password").statusCode()).isEqualTo(403);
            assertThat(login(http, url(context)).statusCode()).isEqualTo(403);
        }
    }

    @Test
    void disabledForceLogoutFilterIsNotAutomaticallyRegisteredByTheServletContainer() throws Exception {
        try (var context = start(false, true, false, false)) {
            String url = url(context);
            CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();
            assertThat(login(http, url).statusCode()).isEqualTo(302);
            assertThat(get(http, url, null).statusCode()).isEqualTo(200);
            SessionRegistry registry = context.getBean(SessionRegistry.class);
            assertThat(registry.getAllPrincipals()).isEmpty();
            assertThat(context.getBeansOfType(SecuritySessionRegistrationListener.class)).isEmpty();
            String id = cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("JSESSIONID"))
                    .findFirst().orElseThrow().getValue();
            registry.registerNewSession(id, "user");
            registry.getSessionInformation(id).expireNow();
            assertThat(get(http, url, null).statusCode()).isEqualTo(200);
        }
    }

    private ConfigurableApplicationContext start(boolean custom, boolean form, boolean basic, boolean forceLogout) {
        return start(custom, form, basic, forceLogout, true);
    }

    private ConfigurableApplicationContext start(boolean custom, boolean form, boolean basic, boolean forceLogout, boolean bootSecurity) {
        return new SpringApplicationBuilder(custom ? CustomHost.class : Host.class).run(
                "--server.port=0", "--server.address=127.0.0.1", "--spring.main.banner-mode=off", "--logging.level.root=OFF",
                "--moli.security.jwt.enabled=false", "--moli.security.form-login-enabled=" + form,
                "--moli.security.http-basic-enabled=" + basic, "--moli.security.force-logout-filter-enabled=" + forceLogout,
                "--spring.autoconfigure.exclude=" + (bootSecurity ? "" : String.join(",",
                        "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration",
                        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
                        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
                        "org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration")));
    }

    private String url(ConfigurableApplicationContext context) {
        return "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    private HttpResponse<String> login(HttpClient http, String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url + "/login"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=user&password=password")).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(HttpClient http, String url, String credentials) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url + "/private")).timeout(Duration.ofSeconds(10));
        if (credentials != null) {
            request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
        return http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(Endpoint.class)
    static class Host {
        @Bean
        AuthenticationManager authenticationManager() {
            return authentication -> {
                if (!"user".equals(authentication.getName()) || !"password".equals(authentication.getCredentials())) {
                    throw new BadCredentialsException("Bad credentials");
                }
                var user = User.withUsername("user").password("unused").roles("USER").build();
                return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(Host.class)
    static class CustomHost {
        @Bean
        SecurityFilterChain hostChain(HttpSecurity http) throws Exception {
            return http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(requests -> requests.requestMatchers("/login", "/error").permitAll().anyRequest().authenticated())
                    .formLogin(Customizer.withDefaults()).build();
        }
    }

    @RestController
    static class Endpoint {
        @GetMapping("/private")
        String privateEndpoint() {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        }
    }
}
