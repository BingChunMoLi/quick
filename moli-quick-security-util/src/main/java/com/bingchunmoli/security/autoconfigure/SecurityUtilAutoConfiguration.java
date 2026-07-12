package com.bingchunmoli.security.autoconfigure;

import com.bingchunmoli.security.context.SecurityUserContext;
import com.bingchunmoli.security.jwt.InMemoryJwtSessionStore;
import com.bingchunmoli.security.jwt.JwtAuthenticationFilter;
import com.bingchunmoli.security.jwt.JwtSessionStore;
import com.bingchunmoli.security.jwt.JwtTokenProperties;
import com.bingchunmoli.security.jwt.JwtTokenResolver;
import com.bingchunmoli.security.jwt.JwtTokenService;
import com.bingchunmoli.security.logout.SecurityLogoutService;
import com.bingchunmoli.security.web.QuickSecurityAuthController;
import jakarta.servlet.http.HttpSessionEvent;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.bind.annotation.RestController;
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
                                                       JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {
        if (!properties.isCsrfEnabled()) {
            http.csrf(AbstractHttpConfigurer::disable);
        }
        if (!properties.isFormLoginEnabled()) {
            http.formLogin(AbstractHttpConfigurer::disable);
        }
        if (!properties.isHttpBasicEnabled()) {
            http.httpBasic(AbstractHttpConfigurer::disable);
        }
        if (properties.getJwt().isEnabled()) {
            http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
            http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        }
        http.authorizeHttpRequests(authorize -> {
            for (String pattern : properties.permitAllPatterns()) {
                authorize.requestMatchers(pattern).permitAll();
            }
            if (properties.getForceLogoutAuthority() != null && !properties.getForceLogoutAuthority().isBlank()) {
                authorize.requestMatchers(properties.forceLogoutPathPattern())
                        .hasAuthority(properties.getForceLogoutAuthority());
            }
            authorize.anyRequest().authenticated();
        });
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({AuthenticationManager.class, JwtTokenService.class})
    @ConditionalOnClass(RestController.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "moli.security", name = "auth-controller-enabled", havingValue = "true",
            matchIfMissing = true)
    public QuickSecurityAuthController quickSecurityAuthController(AuthenticationManager authenticationManager,
                                                                  SessionRegistry sessionRegistry,
                                                                  SecurityLogoutService logoutService,
                                                                  JwtTokenService jwtTokenService) {
        return new QuickSecurityAuthController(authenticationManager, sessionRegistry, logoutService, jwtTokenService);
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
    @ConditionalOnProperty(prefix = "moli.security", name = "force-logout-filter-enabled", havingValue = "true",
            matchIfMissing = true)
    public FilterRegistrationBean<SecurityForceLogoutFilter> securityForceLogoutFilterRegistration(
            SecurityForceLogoutFilter securityForceLogoutFilter,
            SecurityUtilProperties properties) {
        FilterRegistrationBean<SecurityForceLogoutFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(securityForceLogoutFilter);
        registrationBean.setName("securityForceLogoutFilter");
        registrationBean.setOrder(properties.getFilterOrder());
        return registrationBean;
    }
}
