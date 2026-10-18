package com.bingchunmoli.oauth.client.web;

import com.bingchunmoli.oauth.client.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/** Never falls back to an HttpSession authorized-client repository. */
public final class ServiceAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {
    private final OAuth2AuthorizedClientService service;
    private final RefreshCoordinator coordinator;
    public ServiceAuthorizedClientRepository(OAuth2AuthorizedClientService service, RefreshCoordinator coordinator) {
        this.service = service; this.coordinator = coordinator;
    }
    @Override public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String id, Authentication principal,
                                                                             HttpServletRequest request) {
        return principal == null ? null : service.loadAuthorizedClient(id, principal.getName());
    }
    @Override public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal,
                                              HttpServletRequest request, HttpServletResponse response) {
        var ref = new AuthorizedClientRef(client.getClientRegistration().getRegistrationId(), client.getPrincipalName());
        try (var lease = coordinator.acquire(ref)) {
            lease.checkOwned();
            OAuth2AuthorizedClient normalized = TokenResponseMetadata.applyAndClear(client);
            // Providers such as Google may return a refresh token only on the first consent.
            OAuth2AuthorizedClient previous = service.loadAuthorizedClient(ref.registrationId(), ref.principalName());
            var refresh = normalized.getRefreshToken();
            if (previous != null && previous.getRefreshToken() != null && (refresh == null ||
                    refresh.getExpiresAt() == null && previous.getRefreshToken().getExpiresAt() != null &&
                            refresh.getTokenValue().equals(previous.getRefreshToken().getTokenValue()))) {
                normalized = new OAuth2AuthorizedClient(normalized.getClientRegistration(), normalized.getPrincipalName(),
                        normalized.getAccessToken(), previous.getRefreshToken());
            }
            lease.checkOwned();
            service.saveAuthorizedClient(normalized, principal);
        }
    }
    @Override public void removeAuthorizedClient(String id, Authentication principal,
                                                HttpServletRequest request, HttpServletResponse response) {
        if (principal == null) return;
        try (var lease = coordinator.acquire(new AuthorizedClientRef(id, principal.getName()))) {
            lease.checkOwned(); service.removeAuthorizedClient(id, principal.getName());
        }
    }
}
