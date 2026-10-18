package com.bingchunmoli.oauth.client.autoconfigure;

import com.bingchunmoli.oauth.client.*;
import com.bingchunmoli.oauth.client.web.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.*;
import static org.assertj.core.api.Assertions.*;

class QuickOAuth2WebValidationTest {
    WebApplicationContextRunner runner() {
        return new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(QuickOAuth2AutoConfiguration.class, QuickOAuth2WebAutoConfiguration.class))
                .withPropertyValues("moli.oauth.client.enabled=true", "moli.oauth.client.login.enabled=true",
                        "spring.security.oauth2.client.registration.app.client-id=id",
                        "spring.security.oauth2.client.registration.app.authorization-grant-type=authorization_code",
                        "spring.security.oauth2.client.registration.app.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
                        "spring.security.oauth2.client.registration.app.provider=custom",
                        "spring.security.oauth2.client.provider.custom.authorization-uri=https://provider/authorize",
                        "spring.security.oauth2.client.provider.custom.token-uri=https://provider/token",
                        "spring.security.oauth2.client.provider.custom.user-info-uri=https://provider/user",
                        "spring.security.oauth2.client.provider.custom.user-name-attribute=id");
    }
    @Configuration(proxyBeanMethods = false) @EnableWebSecurity
    static class WithoutConfigurer {
        @Bean SecurityFilterChain chain(HttpSecurity http) {
            http.authorizeHttpRequests(r -> r.anyRequest().permitAll()); return http.build();
        }
    }
    @Configuration(proxyBeanMethods = false)
    static class WithHandler {
        @Bean OAuth2LoginHandler handler() { return (identity, user, ref, request, response) -> response.setStatus(204); }
    }
    @Test void enabledLoginRequiresHostHandler() {
        runner().withUserConfiguration(WithoutConfigurer.class).run(context -> assertThat(context).hasFailed());
    }
    @Test void enabledLoginRequiresHostToApplyConfigurer() {
        runner().withUserConfiguration(WithoutConfigurer.class, WithHandler.class)
                .run(context -> assertThat(context).hasFailed().getFailure().hasMessageContaining("configurer"));
    }
    @Test void customServiceWinsOverSelectedDefaultStore() {
        runner().withPropertyValues("moli.oauth.client.login.enabled=false", "moli.oauth.client.authorized-client.store=jdbc")
                .withBean(org.springframework.security.oauth2.client.OAuth2AuthorizedClientService.class,
                        () -> org.mockito.Mockito.mock(org.springframework.security.oauth2.client.OAuth2AuthorizedClientService.class))
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(QuickOAuth2Client.class));
    }
}
