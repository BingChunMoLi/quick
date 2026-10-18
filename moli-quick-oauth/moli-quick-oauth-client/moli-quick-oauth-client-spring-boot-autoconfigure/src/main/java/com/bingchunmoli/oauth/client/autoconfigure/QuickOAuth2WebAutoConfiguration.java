package com.bingchunmoli.oauth.client.autoconfigure;

import com.bingchunmoli.oauth.client.*;
import com.bingchunmoli.oauth.client.web.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.oidc.userinfo.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.*;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

@AutoConfiguration(after = QuickOAuth2AutoConfiguration.class,
        afterName = "com.bingchunmoli.oauth.client.redis.RedisOAuth2WebAutoConfiguration")
@ConditionalOnClass(HttpServletRequest.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "moli.oauth.client", name = {"enabled", "login.enabled"}, havingValue = "true")
public class QuickOAuth2WebAutoConfiguration {
    @Bean @ConditionalOnMissingBean(AuthorizationRequestRepository.class)
    AuthorizationRequestRepository<OAuth2AuthorizationRequest> quickAuthorizationRequests(QuickOAuth2Properties properties) {
        if (!"session".equals(properties.getAuthorizationState().getStore())) {
            throw new IllegalStateException("Selected OAuth2 authorization-state store requires its module and connection bean");
        }
        return new SessionAuthorizationRequestRepository(properties.getAuthorizationState().getTtl());
    }
    @Bean @ConditionalOnMissingBean(OAuth2AuthorizedClientRepository.class)
    OAuth2AuthorizedClientRepository quickWebAuthorizedClients(OAuth2AuthorizedClientService service, RefreshCoordinator coordinator) {
        return new ServiceAuthorizedClientRepository(service, coordinator);
    }
    @Bean(name = "quickOAuth2UserService") @ConditionalOnMissingBean(name = "quickOAuth2UserService")
    OAuth2UserService<OAuth2UserRequest, OAuth2User> quickOAuth2UserService(OAuth2ProviderRegistry registry) {
        var fallback = new DefaultOAuth2UserService();
        return request -> {
            var adapter = registry.adapter(request.getClientRegistration().getRegistrationId());
            var service = adapter == null ? null : adapter.userService();
            return (service == null ? fallback : service).loadUser(request);
        };
    }
    @Bean(name = "quickOidcUserService") @ConditionalOnMissingBean(name = "quickOidcUserService")
    OAuth2UserService<OidcUserRequest, OidcUser> quickOidcUserService() { return new OidcUserService(); }

    @Bean(name = "quickOAuth2FailureHandler") @ConditionalOnMissingBean(name = "quickOAuth2FailureHandler")
    AuthenticationFailureHandler quickOAuth2FailureHandler() {
        return (request, response, failure) -> response.sendError(401, "OAuth2 authorization failed");
    }
    @Bean @ConditionalOnMissingBean
    QuickOAuth2WebSupport quickOAuth2WebSupport(ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientRepository clients, AuthorizationRequestRepository<OAuth2AuthorizationRequest> states,
            OAuth2ProviderRegistry registry, ProviderTokenClients tokenClients,
            @org.springframework.beans.factory.annotation.Qualifier("quickOAuth2UserService") OAuth2UserService<OAuth2UserRequest, OAuth2User> userService,
            @org.springframework.beans.factory.annotation.Qualifier("quickOidcUserService") OAuth2UserService<OidcUserRequest, OidcUser> oidcService,
            ObjectProvider<OAuth2LoginHandler> handler,
            @org.springframework.beans.factory.annotation.Qualifier("quickOAuth2FailureHandler") AuthenticationFailureHandler failure,
            QuickOAuth2Properties properties) {
        OAuth2LoginHandler selected = handler.getIfAvailable();
        if (selected == null) throw new IllegalStateException("OAuth2 login requires a host OAuth2LoginHandler");
        return new QuickOAuth2WebSupport(registrations, clients, states, registry, tokenClients,
                userService, oidcService, selected, failure, properties);
    }
    @Bean
    SmartInitializingSingleton quickOAuth2ChainValidation(QuickOAuth2WebSupport support) {
        return () -> {
            if (!support.isApplied()) throw new IllegalStateException("Apply the Quick OAuth2 configurer to the host SecurityFilterChain");
        };
    }
}
