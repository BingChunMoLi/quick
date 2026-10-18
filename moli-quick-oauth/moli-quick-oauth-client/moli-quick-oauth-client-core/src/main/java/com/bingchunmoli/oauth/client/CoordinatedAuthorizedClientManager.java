package com.bingchunmoli.oauth.client;

import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/** Re-reads the stored client only after coordination, including for non-web callers. */
public final class CoordinatedAuthorizedClientManager implements OAuth2AuthorizedClientManager {
    private final RefreshCoordinator coordinator;
    private final AuthorizedClientServiceOAuth2AuthorizedClientManager delegate;
    private final OAuth2AuthorizedClientService service;
    private final ThreadLocal<RefreshCoordinator.Lease> current = new ThreadLocal<>();

    public CoordinatedAuthorizedClientManager(ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService service, OAuth2AuthorizedClientProvider provider,
            RefreshCoordinator coordinator) {
        this.coordinator = coordinator;
        this.service = service;
        delegate = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, service);
        delegate.setAuthorizedClientProvider(provider);
        delegate.setAuthorizationSuccessHandler((client, principal, attributes) -> {
            current.get().checkOwned();
            OAuth2AuthorizedClient normalized = TokenResponseMetadata.applyAndClear(client);
            OAuth2AuthorizedClient previous = service.loadAuthorizedClient(client.getClientRegistration().getRegistrationId(), principal.getName());
            var refresh = normalized.getRefreshToken();
            if (refresh != null && refresh.getExpiresAt() == null && previous != null && previous.getRefreshToken() != null &&
                    refresh.getTokenValue().equals(previous.getRefreshToken().getTokenValue()) &&
                    previous.getRefreshToken().getExpiresAt() != null) {
                normalized = new OAuth2AuthorizedClient(client.getClientRegistration(), client.getPrincipalName(), normalized.getAccessToken(),
                        new org.springframework.security.oauth2.core.OAuth2RefreshToken(refresh.getTokenValue(), refresh.getIssuedAt(),
                                previous.getRefreshToken().getExpiresAt()));
            }
            current.get().checkOwned();
            service.saveAuthorizedClient(normalized, principal);
        });
        delegate.setAuthorizationFailureHandler((failure, principal, attributes) -> {
            if ("invalid_grant".equals(failure.getError().getErrorCode()) &&
                    failure instanceof org.springframework.security.oauth2.client.ClientAuthorizationException clientFailure) {
                current.get().checkOwned();
                service.removeAuthorizedClient(clientFailure.getClientRegistrationId(), principal.getName());
            }
        });
    }

    @Override public OAuth2AuthorizedClient authorize(OAuth2AuthorizeRequest request) {
        if (current.get() != null) throw new IllegalStateException("Nested authorization on the same OAuth2 manager is unsupported");
        AuthorizedClientRef ref = new AuthorizedClientRef(request.getClientRegistrationId(), request.getPrincipal().getName());
        try (TokenResponseMetadata.Scope scope = TokenResponseMetadata.scope();
             RefreshCoordinator.Lease lease = coordinator.acquire(ref)) {
            current.set(lease);
            // Do not accept a caller-supplied stale authorizedClient.
            OAuth2AuthorizedClient stored = service.loadAuthorizedClient(ref.registrationId(), ref.principalName());
            if (stored != null && stored.getRefreshToken() != null && stored.getRefreshToken().getExpiresAt() != null &&
                    !stored.getRefreshToken().getExpiresAt().isAfter(java.time.Instant.now()) &&
                    stored.getAccessToken().getExpiresAt() != null &&
                    !stored.getAccessToken().getExpiresAt().isAfter(java.time.Instant.now())) {
                lease.checkOwned();
                service.removeAuthorizedClient(ref.registrationId(), ref.principalName());
                throw new AuthorizationRequiredException();
            }
            OAuth2AuthorizedClient result = delegate.authorize(OAuth2AuthorizeRequest.withClientRegistrationId(ref.registrationId())
                    .principal(request.getPrincipal()).attributes(a -> a.putAll(request.getAttributes())).build());
            OAuth2AuthorizedClient saved = service.loadAuthorizedClient(ref.registrationId(), ref.principalName());
            return saved != null ? saved : result;
        } finally {
            current.remove();
        }
    }
}
