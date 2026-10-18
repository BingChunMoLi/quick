package com.bingchunmoli.oauth.client;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.client.RestClient;

public final class QuickOAuth2Client {
    private final OAuth2AuthorizedClientManager manager;
    private final OAuth2AuthorizedClientService service;
    private final RefreshCoordinator coordinator;
    public QuickOAuth2Client(OAuth2AuthorizedClientManager manager, OAuth2AuthorizedClientService service,
                             RefreshCoordinator coordinator) {
        this.manager = manager;
        this.service = service;
        this.coordinator = coordinator;
    }
    public OAuth2AuthorizedClient authorize(AuthorizedClientRef ref) {
        try {
            OAuth2AuthorizedClient client = manager.authorize(OAuth2AuthorizeRequest
                    .withClientRegistrationId(ref.registrationId()).principal(principal(ref.principalName())).build());
            if (client == null || (client.getAccessToken().getExpiresAt() != null &&
                    !client.getAccessToken().getExpiresAt().isAfter(java.time.Instant.now()))) {
                throw new AuthorizationRequiredException();
            }
            return client;
        } catch (OAuth2AuthorizationException ex) {
            // Provider error descriptions can contain authorization codes or tokens.
            throw new AuthorizationRequiredException();
        }
    }
    public AuthorizedClientRef application(String registrationId) {
        return new AuthorizedClientRef(registrationId, "quick-oauth2:application:" + registrationId);
    }
    public void remove(AuthorizedClientRef ref) {
        try (RefreshCoordinator.Lease lease = coordinator.acquire(ref)) {
            lease.checkOwned();
            service.removeAuthorizedClient(ref.registrationId(), ref.principalName());
        }
    }
    /** Use a dedicated builder per trusted API. The interceptor does not retry business requests. */
    public RestClient restClient(AuthorizedClientRef ref, RestClient.Builder builder) {
        return builder.clone().requestInterceptor((request, body, execution) -> {
            request.getHeaders().setBearerAuth(authorize(ref).getAccessToken().getTokenValue());
            return execution.execute(request, body);
        }).build();
    }
    public static Authentication principal(String name) {
        return UsernamePasswordAuthenticationToken.authenticated(name, "N/A", java.util.List.of());
    }
}
