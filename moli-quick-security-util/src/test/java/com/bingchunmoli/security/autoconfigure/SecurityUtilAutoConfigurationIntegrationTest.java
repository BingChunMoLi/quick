package com.bingchunmoli.security.autoconfigure;

import com.bingchunmoli.security.context.SecurityUserContext;
import com.bingchunmoli.security.logout.SecurityLogoutService;
import com.bingchunmoli.security.web.QuickSecurityAuthController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityUtilAutoConfigurationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityUtilAutoConfiguration.class));

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityUtilAutoConfiguration.class))
            .withUserConfiguration(WebSecurityConfiguration.class);

    @Test
    void shouldAutoConfigureSecurityUtilityBeans() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(SecurityUserContext.class)
                .hasSingleBean(SessionRegistry.class)
                .hasSingleBean(HttpSessionEventPublisher.class)
                .hasSingleBean(SecurityContextLogoutHandler.class)
                .hasSingleBean(SecurityLogoutService.class)
                .hasSingleBean(SecurityForceLogoutFilter.class)
                .hasBean("securityForceLogoutFilterRegistration"));
    }

    @Test
    void shouldDisableForceLogoutFilterRegistrationByProperty() {
        contextRunner.withPropertyValues("moli.security.force-logout-filter-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(SecurityForceLogoutFilter.class);
                    assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
                });
    }

    @Test
    void shouldAutoConfigureDefaultSecurityIntegrationInWebApplication() {
        webContextRunner.run(context -> assertThat(context)
                .hasSingleBean(SecurityFilterChain.class)
                .hasSingleBean(QuickSecurityAuthController.class)
                .hasSingleBean(SecurityLogoutService.class)
                .hasSingleBean(SessionRegistry.class));
    }

    @Test
    void shouldBackOffDefaultSecurityFilterChainWhenConsumerProvidesOne() {
        webContextRunner.withUserConfiguration(CustomSecurityFilterChainConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SecurityFilterChain.class);
                    assertThat(context.getBean(SecurityFilterChain.class))
                            .isSameAs(context.getBean("customSecurityFilterChain"));
                });
    }

    @Test
    void shouldBackOffWhenConsumerProvidesBeans() {
        contextRunner.withUserConfiguration(CustomSecurityUtilConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SecurityUserContext.class);
                    assertThat(context).hasSingleBean(SessionRegistry.class);
                    assertThat(context).hasSingleBean(SecurityContextLogoutHandler.class);
                    assertThat(context).hasSingleBean(SecurityLogoutService.class);
                    assertThat(context.getBean(SecurityUserContext.class))
                            .isSameAs(context.getBean("customSecurityUserContext"));
                    assertThat(context.getBean(SessionRegistry.class))
                            .isSameAs(context.getBean("customSessionRegistry"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomSecurityUtilConfiguration {

        @Bean
        SecurityUserContext customSecurityUserContext() {
            return new SecurityUserContext();
        }

        @Bean
        SessionRegistry customSessionRegistry() {
            return new SessionRegistryImpl();
        }

        @Bean
        SecurityContextLogoutHandler customLogoutHandler() {
            return new SecurityContextLogoutHandler();
        }

        @Bean
        SecurityLogoutService customSecurityLogoutService(SessionRegistry sessionRegistry,
                                                          SecurityContextLogoutHandler logoutHandler) {
            return new SecurityLogoutService(sessionRegistry, logoutHandler);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class WebSecurityConfiguration {

        @Bean
        AuthenticationManager authenticationManager() {
            return authentication -> UsernamePasswordAuthenticationToken.authenticated(
                    authentication.getPrincipal(), authentication.getCredentials(), authentication.getAuthorities());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomSecurityFilterChainConfiguration {

        @Bean
        SecurityFilterChain customSecurityFilterChain() {
            return new DefaultSecurityFilterChain(AnyRequestMatcher.INSTANCE, List.of());
        }
    }
}
