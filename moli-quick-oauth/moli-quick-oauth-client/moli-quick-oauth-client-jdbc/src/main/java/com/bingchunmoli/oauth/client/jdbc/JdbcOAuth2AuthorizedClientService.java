package com.bingchunmoli.oauth.client.jdbc;

import com.bingchunmoli.oauth.client.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/** Explicit schema, portable DML and full refresh-expiry persistence. */
public final class JdbcOAuth2AuthorizedClientService implements OAuth2AuthorizedClientService {
    private final JdbcOperations jdbc;
    private final ClientRegistrationRepository registrations;
    public JdbcOAuth2AuthorizedClientService(JdbcOperations jdbc, ClientRegistrationRepository registrations) {
        this.jdbc = jdbc; this.registrations = registrations;
    }
    @Override @SuppressWarnings("unchecked")
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String id, String principal) {
        var values = jdbc.query("SELECT token_data FROM moli_oauth2_authorized_client WHERE authorization_key = ?",
                (rs, row) -> rs.getString(1), TokenSnapshotCodec.key(new AuthorizedClientRef(id, principal)));
        if (values.isEmpty()) return null;
        OAuth2AuthorizedClient client = TokenSnapshotCodec.decode(values.get(0), registrations);
        if (!id.equals(client.getClientRegistration().getRegistrationId()) || !principal.equals(client.getPrincipalName())) {
            throw new IllegalStateException("OAuth2 authorization identity mismatch");
        }
        return (T) client;
    }
    @Override public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal) {
        String key = TokenSnapshotCodec.key(new AuthorizedClientRef(client.getClientRegistration().getRegistrationId(), client.getPrincipalName()));
        String data = TokenSnapshotCodec.encode(client);
        if (jdbc.update("UPDATE moli_oauth2_authorized_client SET token_data = ? WHERE authorization_key = ?", data, key) == 0) {
            try { jdbc.update("INSERT INTO moli_oauth2_authorized_client (authorization_key, token_data) VALUES (?, ?)", key, data); }
            catch (DuplicateKeyException concurrentInsert) {
                jdbc.update("UPDATE moli_oauth2_authorized_client SET token_data = ? WHERE authorization_key = ?", data, key);
            }
        }
    }
    @Override public void removeAuthorizedClient(String id, String principal) {
        jdbc.update("DELETE FROM moli_oauth2_authorized_client WHERE authorization_key = ?",
                TokenSnapshotCodec.key(new AuthorizedClientRef(id, principal)));
    }
}
