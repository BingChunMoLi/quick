package com.bingchunmoli.security.autoconfigure;

import com.bingchunmoli.security.context.SecurityUserContext;
import com.bingchunmoli.security.jwt.JwtAuthenticationFilter;
import com.bingchunmoli.security.jwt.JwtSessionStore;
import com.bingchunmoli.security.jwt.JwtTokenProperties;
import com.bingchunmoli.security.jwt.JwtTokenResolver;
import com.bingchunmoli.security.jwt.JwtTokenService;
import com.bingchunmoli.security.logout.SecurityLogoutService;
import jakarta.servlet.http.HttpSessionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security quick utility auto configuration.
 *
 * @author MoLi
 */
@AutoConfiguration
@ConditionalOnClass({SecurityContextHolder.class, Authentication.class})
@ConditionalOnProperty(prefix = "moli.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SecurityUtilProperties.class)
public class SecurityUtilAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityUtilAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SecurityUserContext securityUserContext() {
        return new SecurityUserContext();
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(HttpSessionEvent.class)
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "moli.security", name = "force-logout-filter-enabled", havingValue = "true",
            matchIfMissing = true)
    public SecuritySessionRegistrationListener securitySessionRegistrationListener(SessionRegistry sessionRegistry) {
        return new SecuritySessionRegistrationListener(sessionRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityContextLogoutHandler securityContextLogoutHandler() {
        return new SecurityContextLogoutHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityLogoutService securityLogoutService(SessionRegistry sessionRegistry,
                                                       SecurityContextLogoutHandler logoutHandler) {
        return new SecurityLogoutService(sessionRegistry, logoutHandler);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenProperties jwtTokenProperties(SecurityUtilProperties properties) {
        return properties.getJwt();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtSessionStoreFactory jwtSessionStoreFactory(ListableBeanFactory beanFactory) {
        return new JwtSessionStoreFactory(beanFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtSessionStore jwtSessionStore(JwtSessionStoreFactory jwtSessionStoreFactory,
                                           JwtTokenProperties jwtTokenProperties) {
        return jwtSessionStoreFactory.create(jwtTokenProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "moli.security.jwt", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public JwtTokenService jwtTokenService(JwtTokenProperties jwtTokenProperties, JwtSessionStore jwtSessionStore) {
        if (JwtTokenProperties.DEFAULT_SECRET.equals(jwtTokenProperties.getSecret())) {
            log.warn("Using the default JWT secret. For production environments, replace moli.security.jwt.secret with a strong secret.");
        }
        return new JwtTokenService(jwtTokenProperties, jwtSessionStore);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JwtTokenService.class)
    public JwtTokenResolver jwtTokenResolver(JwtTokenProperties jwtTokenProperties) {
        return new JwtTokenResolver(jwtTokenProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JwtTokenService.class)
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenResolver jwtTokenResolver,
                                                           JwtTokenService jwtTokenService) {
        return new JwtAuthenticationFilter(jwtTokenResolver, jwtTokenService);
    }

    @Bean
    @ConditionalOnBean(JwtAuthenticationFilter.class)
    @ConditionalOnMissingBean(name = "jwtAuthenticationFilterRegistration")
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter jwtAuthenticationFilter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(jwtAuthenticationFilter);
        // Authentication must run inside the selected security chain, after its context has been loaded.
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(AuthenticationConfiguration.class)
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    @ConditionalOnClass({HttpSecurity.class, SecurityFilterChain.class})
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "moli.security", name = "default-filter-chain-enabled", havingValue = "true",
            matchIfMissing = true)
    public SecurityFilterChain moliSecurityFilterChain(HttpSecurity http, SecurityUtilProperties properties,
                                                       ObjectProvider<JwtAuthenticationFilter> jwtAuthenticationFilter)
            throws Exception {
        if (!properties.isCsrfEnabled()) {
            http.csrf(AbstractHttpConfigurer::disable);
        }
        if (properties.isFormLoginEnabled()) {
            http.formLogin(form -> form.permitAll());
        } else {
            http.formLogin(AbstractHttpConfigurer::disable);
        }
        if (properties.isHttpBasicEnabled()) {
            http.httpBasic(Customizer.withDefaults());
        } else {
            http.httpBasic(AbstractHttpConfigurer::disable);
        }
        if (properties.getJwt().isEnabled()) {
            http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
            http.addFilterBefore(jwtAuthenticationFilter.getObject(), UsernamePasswordAuthenticationFilter.class);
        }
        http.authorizeHttpRequests(authorize -> {
            for (String pattern : properties.getPermitAll()) {
                authorize.requestMatchers(pattern).permitAll();
            }
            authorize.anyRequest().authenticated();
        });
        return http.build();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "moli.security", name = "default-filter-chain-enabled", havingValue = "true",
            matchIfMissing = true)
    @EnableWebSecurity
    static class DefaultWebSecurityConfiguration {
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(OncePerRequestFilter.class)
    public SecurityForceLogoutFilter securityForceLogoutFilter(SessionRegistry sessionRegistry,
                                                               SecurityContextLogoutHandler logoutHandler,
                                                               SecurityUtilProperties properties) {
        return new SecurityForceLogoutFilter(sessionRegistry, logoutHandler, properties.getExpiredStatus());
    }

    @Bean
    @ConditionalOnMissingBean(name = "securityForceLogoutFilterRegistration")
    @ConditionalOnClass(FilterRegistrationBean.class)
    public FilterRegistrationBean<SecurityForceLogoutFilter> securityForceLogoutFilterRegistration(
            SecurityForceLogoutFilter securityForceLogoutFilter,
            SecurityUtilProperties properties) {
        FilterRegistrationBean<SecurityForceLogoutFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(securityForceLogoutFilter);
        registrationBean.setName("securityForceLogoutFilter");
        registrationBean.setOrder(properties.getFilterOrder());
        registrationBean.setEnabled(properties.isForceLogoutFilterEnabled());
        return registrationBean;
    }
}
