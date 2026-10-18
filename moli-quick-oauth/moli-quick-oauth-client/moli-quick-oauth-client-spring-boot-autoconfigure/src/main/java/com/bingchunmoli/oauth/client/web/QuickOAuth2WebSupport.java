package com.bingchunmoli.oauth.client.web;

import com.bingchunmoli.oauth.client.*;
import com.bingchunmoli.oauth.client.autoconfigure.QuickOAuth2Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.*;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

/** Creates a fresh configurer for each host-owned chain. */
public final class QuickOAuth2WebSupport {
    private final ClientRegistrationRepository registrations;
    private final OAuth2AuthorizedClientRepository clients;
    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> states;
    private final OAuth2ProviderRegistry registry;
    private final ProviderTokenClients tokenClients;
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> userService;
    private final OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService;
    private final OAuth2LoginHandler handler;
    private final AuthenticationFailureHandler failure;
    private final QuickOAuth2Properties properties;
    private final AtomicBoolean applied = new AtomicBoolean();

    public QuickOAuth2WebSupport(ClientRegistrationRepository registrations, OAuth2AuthorizedClientRepository clients,
            AuthorizationRequestRepository<OAuth2AuthorizationRequest> states, OAuth2ProviderRegistry registry,
            ProviderTokenClients tokenClients, OAuth2UserService<OAuth2UserRequest, OAuth2User> userService,
            OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService, OAuth2LoginHandler handler,
            AuthenticationFailureHandler failure, QuickOAuth2Properties properties) {
        this.registrations = registrations; this.clients = clients; this.states = states; this.registry = registry;
        this.tokenClients = tokenClients; this.userService = userService; this.oidcUserService = oidcUserService;
        this.handler = handler; this.failure = failure; this.properties = properties;
    }
    public boolean isApplied() { return applied.get(); }
    public Configurer configurer() { return new Configurer(); }

    public final class Configurer extends AbstractHttpConfigurer<Configurer, HttpSecurity> {
        private Configurer() { }
        @Override public void init(HttpSecurity http) {
            applied.set(true);
            var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, properties.getLogin().getAuthorizationBaseUri());
            resolver.setAuthorizationRequestCustomizer(builder -> {
                OAuth2AuthorizationRequest snapshot = builder.build();
                String id = snapshot.getAttribute("registration_id");
                var adapter = registry.adapter(id);
                if (adapter != null) adapter.customizeAuthorization(builder);
            });
            http.addFilterBefore(new OAuth2CallbackMetadataFilter(properties.getLogin().getCallbackBaseUri()),
                    OAuth2LoginAuthenticationFilter.class);
            http.oauth2Login(login -> login
                    .clientRegistrationRepository(registrations)
                    .authorizedClientRepository(clients)
                    .securityContextRepository(new RequestAttributeSecurityContextRepository())
                    .authorizationEndpoint(endpoint -> endpoint.baseUri(properties.getLogin().getAuthorizationBaseUri())
                            .authorizationRequestRepository(states).authorizationRequestResolver(resolver))
                    .redirectionEndpoint(endpoint -> endpoint.baseUri(properties.getLogin().getCallbackBaseUri()))
                    .tokenEndpoint(endpoint -> endpoint.accessTokenResponseClient(tokenClients.authorizationCode()))
                    .userInfoEndpoint(endpoint -> endpoint.userService(userService).oidcUserService(oidcUserService))
                    .successHandler((request, response, authentication) -> {
                        try {
                            var token = (OAuth2AuthenticationToken) authentication;
                            var registration = registrations.findByRegistrationId(token.getAuthorizedClientRegistrationId());
                            handler.onLogin(registry.identity(registration, token.getPrincipal()), token.getPrincipal(),
                                    new AuthorizedClientRef(registration.getRegistrationId(), token.getName()), request, response);
                        } finally { TokenResponseMetadata.clear(); }
                    })
                    .failureHandler((request, response, exception) -> {
                        TokenResponseMetadata.clear();
                        failure.onAuthenticationFailure(request, response, exception);
                    })
                    .withObjectPostProcessor(new ObjectPostProcessor<Object>() {
                        @Override public <O> O postProcess(O object) {
                            if (object instanceof OAuth2AuthorizationRequestRedirectFilter redirect) {
                                redirect.setRequestCache(new NullRequestCache());
                                redirect.setAuthenticationFailureHandler((request, response, exception) -> {
                                    TokenResponseMetadata.clear();
                                    failure.onAuthenticationFailure(request, response, exception);
                                });
                            }
                            if (object instanceof OAuth2LoginAuthenticationFilter filter) {
                                filter.setSecurityContextRepository(new RequestAttributeSecurityContextRepository());
                                // Only the host may establish or mutate business authentication.
                                filter.setSessionAuthenticationStrategy((authentication, request, response) -> { });
                            }
                            return object;
                        }
                    }));
        }
    }
}
