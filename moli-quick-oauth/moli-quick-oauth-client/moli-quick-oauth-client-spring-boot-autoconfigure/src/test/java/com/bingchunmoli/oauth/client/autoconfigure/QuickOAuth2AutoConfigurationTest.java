package com.bingchunmoli.oauth.client.autoconfigure;

import com.bingchunmoli.oauth.client.*;
import com.bingchunmoli.oauth.client.web.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import static org.assertj.core.api.Assertions.*;

class QuickOAuth2AutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withConfiguration(
            AutoConfigurations.of(QuickOAuth2AutoConfiguration.class, QuickOAuth2WebAutoConfiguration.class));
    private static final String[] CONFIG = {
            "moli.oauth.client.enabled=true", "spring.security.oauth2.client.registration.app.client-id=id",
            "spring.security.oauth2.client.registration.app.client-secret=secret",
            "spring.security.oauth2.client.registration.app.authorization-grant-type=client_credentials",
            "spring.security.oauth2.client.registration.app.provider=custom",
            "spring.security.oauth2.client.provider.custom.token-uri=https://provider/token"
    };
    @Test void disabledRegistersNothing() {
        runner.run(context -> assertThat(context).doesNotHaveBean(QuickOAuth2Client.class));
    }
    @Test void nonWebClientWorksWithNoServletClasses() {
        runner.withClassLoader(new FilteredClassLoader("jakarta.servlet")).withPropertyValues(CONFIG).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(QuickOAuth2Client.class);
            assertThat(context).doesNotHaveBean(QuickOAuth2WebSupport.class);
        });
    }
    @Test void explicitlySelectedMissingStoreAndCoordinatorFail() {
        runner.withPropertyValues(CONFIG).withPropertyValues("moli.oauth.client.authorized-client.store=redis")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(CONFIG).withPropertyValues("moli.oauth.client.refresh.coordination=redis")
                .run(context -> assertThat(context).hasFailed());
    }
    @Test void nativeSecurityImportIsOnlySuppressedWhenQuickEnabled() {
        var filter = new QuickOAuth2ImportFilter();
        var env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of("moli.oauth.client.enabled", "true")));
        filter.setEnvironment(env);
        String nativeWeb = "org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration";
        assertThat(filter.match(new String[]{nativeWeb, "unrelated", null}, null)).containsExactly(false, true, true);
        env.getPropertySources().remove("test");
        assertThat(filter.match(new String[]{nativeWeb}, null)).containsExactly(true);
    }
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    static class BootDiscovery { }

    @Test void bootDiscoveryLoadsImportFilterAndPreservesNativeBehaviorWhenDisabled() {
        var web = new org.springframework.boot.test.context.runner.WebApplicationContextRunner()
                .withUserConfiguration(BootDiscovery.class).withPropertyValues(CONFIG);
        web.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(QuickOAuth2Client.class);
            var filters = context.getBean(org.springframework.security.web.FilterChainProxy.class);
            assertThat(filters.getFilterChains().stream().flatMap(chain -> chain.getFilters().stream()))
                    .noneMatch(filter -> filter instanceof org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter);
        });
        web.withPropertyValues("moli.oauth.client.enabled=false").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(QuickOAuth2Client.class);
            var filters = context.getBean(org.springframework.security.web.FilterChainProxy.class);
            assertThat(filters.getFilterChains().stream().flatMap(chain -> chain.getFilters().stream()))
                    .anyMatch(filter -> filter instanceof org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter);
        });
    }
    @Test void sessionStateSupportsParallelTabsAndSingleConsumption() {
        var repository = new SessionAuthorizationRequestRepository(Duration.ofMinutes(10));
        var first = new MockHttpServletRequest(); var response = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(authorization("one"), first, response);
        var second = new MockHttpServletRequest(); second.setSession(first.getSession());
        repository.saveAuthorizationRequest(authorization("two"), second, response);
        first.setParameter("state", "one"); second.setParameter("state", "two");
        assertThat(repository.removeAuthorizationRequest(first, response).getState()).isEqualTo("one");
        assertThat(repository.removeAuthorizationRequest(first, response)).isNull();
        assertThat(repository.removeAuthorizationRequest(second, response).getState()).isEqualTo("two");
        var foreign = new MockHttpServletRequest(); foreign.setParameter("state", "one");
        assertThat(repository.removeAuthorizationRequest(foreign, response)).isNull();
        assertThat(foreign.getSession(false)).isNull();
    }
    @Test void stateCodecKeepsPkceAndNonceWithoutTypeMetadata() {
        var codec = new AuthorizationRequestCodec();
        var request = authorization("state");
        String json = codec.encode(request);
        var restored = codec.decode(json);
        assertThat(restored.getAttributes()).containsEntry("code_verifier", "verifier").containsEntry("nonce", "nonce-value");
        assertThat(restored.getAdditionalParameters()).containsEntry("code_challenge_method", "S256");
        assertThat(json).doesNotContain("@class", "clientSecret");
        assertThatThrownBy(() -> codec.decode("{\"version\":2}")).hasMessage("Invalid OAuth2 authorization state");
    }
    private static OAuth2AuthorizationRequest authorization(String state) {
        return OAuth2AuthorizationRequest.authorizationCode().authorizationUri("https://provider/authorize")
                .clientId("client").redirectUri("https://host/callback").state(state).scopes(Set.of("openid"))
                .attributes(a -> { a.put("registration_id", "app"); a.put("code_verifier", "verifier"); a.put("nonce", "nonce-value"); })
                .additionalParameters(Map.of("code_challenge_method", "S256")).build();
    }
}
