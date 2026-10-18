package com.bingchunmoli.oauth.client;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

/** Bridges protocol response metadata omitted by Spring's AuthorizedClient constructor. */
public final class TokenResponseMetadata {
    private record Metadata(Instant refreshExpiresAt, boolean nonExpiringAccess) { }
    private static final ThreadLocal<Map<String, Metadata>> CURRENT = ThreadLocal.withInitial(HashMap::new);
    private TokenResponseMetadata() { }
    public static void capture(String registrationId, OAuth2AccessTokenResponse response) {
        Object seconds = response.getAdditionalParameters().get("refresh_token_expires_in");
        Instant expiration = null;
        if (seconds != null && response.getRefreshToken() != null) {
            try {
                long duration = Long.parseLong(seconds.toString());
                if (duration <= 0) throw new IllegalArgumentException();
                expiration = response.getRefreshToken().getIssuedAt().plusSeconds(duration);
            } catch (RuntimeException ex) { throw new IllegalStateException("Invalid OAuth2 refresh expiration"); }
        }
        CURRENT.get().put(key(registrationId, response.getAccessToken().getTokenValue()),
                new Metadata(expiration, Boolean.TRUE.equals(response.getAdditionalParameters().get("quick_non_expiring_access"))));
    }
    public static OAuth2AuthorizedClient applyAndClear(OAuth2AuthorizedClient client) {
        Metadata metadata = CURRENT.get().remove(key(client.getClientRegistration().getRegistrationId(), client.getAccessToken().getTokenValue()));
        if (CURRENT.get().isEmpty()) clear();
        if (metadata == null) return client;
        OAuth2AccessToken access = client.getAccessToken();
        if (metadata.nonExpiringAccess()) access = new OAuth2AccessToken(access.getTokenType(), access.getTokenValue(),
                access.getIssuedAt(), null, access.getScopes());
        OAuth2RefreshToken refresh = client.getRefreshToken();
        if (refresh != null && metadata.refreshExpiresAt() != null) {
            refresh = new OAuth2RefreshToken(refresh.getTokenValue(), refresh.getIssuedAt(), metadata.refreshExpiresAt());
        }
        return new OAuth2AuthorizedClient(client.getClientRegistration(), client.getPrincipalName(), access, refresh);
    }
    public static void clear() { CURRENT.remove(); }
    private static String key(String registrationId, String token) {
        return TokenSnapshotCodec.key(new AuthorizedClientRef(registrationId, token));
    }
    /** A nested API authorization must not discard a pending browser login's metadata. */
    public static Scope scope() {
        Map<String, Metadata> previous = new HashMap<>(CURRENT.get());
        return () -> { if (previous.isEmpty()) clear(); else CURRENT.set(previous); };
    }
    public interface Scope extends AutoCloseable { @Override void close(); }
}
