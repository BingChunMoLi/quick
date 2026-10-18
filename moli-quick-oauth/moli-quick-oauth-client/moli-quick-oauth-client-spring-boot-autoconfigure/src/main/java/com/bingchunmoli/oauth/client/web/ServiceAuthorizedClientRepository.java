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
            service.saveAuthorizedClient(TokenResponseMetadata.applyAndClear(client), principal);
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
