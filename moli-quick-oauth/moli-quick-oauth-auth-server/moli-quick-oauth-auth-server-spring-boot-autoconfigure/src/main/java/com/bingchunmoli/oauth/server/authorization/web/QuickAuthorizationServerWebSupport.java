package com.bingchunmoli.oauth.server.authorization.web;
import com.bingchunmoli.oauth.server.authorization.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.OpaqueTokenAuthenticationProvider;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
public final class QuickAuthorizationServerWebSupport {
    private final RegisteredClientRepository clients;
    private final OAuth2AuthorizationService authorizations;
    private final OAuth2AuthorizationConsentService consents;
    private final AuthorizationServerSettings settings;
    private final OAuth2TokenGenerator<?> generator;
    private final GrantCoordinator coordinator;
    private final org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    private final AtomicBoolean applied = new AtomicBoolean();
    public QuickAuthorizationServerWebSupport(RegisteredClientRepository clients, OAuth2AuthorizationService authorizations,
            OAuth2AuthorizationConsentService consents, AuthorizationServerSettings settings,
            OAuth2TokenGenerator<?> generator, GrantCoordinator coordinator, org.springframework.security.oauth2.jwt.JwtDecoder decoder) {
        this.clients = clients; this.authorizations = authorizations; this.consents = consents; this.settings = settings;
        this.generator = generator; this.coordinator = coordinator;
        this.decoder=decoder;
    }
    public boolean isApplied() { return applied.get(); }
    public Configurer configurer() { return new Configurer(); }
    public final class Configurer extends AbstractHttpConfigurer<Configurer, HttpSecurity> {
        private final OAuth2AuthorizationServerConfigurer nativeConfigurer = new OAuth2AuthorizationServerConfigurer();
        public RequestMatcher endpointsMatcher() { return nativeConfigurer.getEndpointsMatcher(); }
        @Override public void init(HttpSecurity http) {
            applied.set(true);
            http.setSharedObject(org.springframework.security.oauth2.jwt.JwtDecoder.class,decoder);
            var jwt=new ProviderManager(new JwtAuthenticationProvider(decoder));
            var userInfo=new ProviderManager(new OpaqueTokenAuthenticationProvider(new AuthorizationServiceTokenIntrospector(authorizations)));
            var userInfoEndpoint=PathPatternRequestMatcher.withDefaults().matcher(settings.getOidcUserInfoEndpoint());
            // Only UserInfo uses local token state; other protocol endpoints retain JWT bearer validation.
            http.oauth2ResourceServer(resource -> resource.authenticationManagerResolver(request -> userInfoEndpoint.matches(request)?userInfo:jwt));
            http.with(nativeConfigurer, server -> server.registeredClientRepository(clients).authorizationService(authorizations).authorizationConsentService(consents)
                .authorizationServerSettings(settings).tokenGenerator(generator).oidc(Customizer.withDefaults())
                .authorizationEndpoint(endpoint -> endpoint.authenticationProviders(providers -> providers.replaceAll(
                    // Native configuration still needs the concrete request provider to obtain its validators.
                    provider -> provider instanceof org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationProvider ?
                        new CoordinatingGrantProvider(provider, authorizations, coordinator) : provider)))
                .tokenEndpoint(endpoint -> endpoint.authenticationProviders(providers -> providers.replaceAll(
                    provider -> new CoordinatingGrantProvider(provider, authorizations, coordinator))))
                .tokenRevocationEndpoint(endpoint -> endpoint.authenticationProviders(providers -> providers.replaceAll(
                    provider -> new CoordinatingGrantProvider(provider, authorizations, coordinator)))));
        }
    }
}
