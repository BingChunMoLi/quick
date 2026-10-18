package com.bingchunmoli.oauth.client.jdbc;

import com.bingchunmoli.oauth.client.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import static org.assertj.core.api.Assertions.*;

/** Run -Poauth-it against dedicated databases. Never uses H2 or a shared business schema. */
class JdbcAuthorizedClientIT {
    @ParameterizedTest @ValueSource(strings = {"MYSQL", "POSTGRESQL"})
    void realDatabaseRoundTripRestartUpdateAndRemoval(String database) {
        String prefix = "OAUTH_TEST_" + database + "_";
        String url = System.getenv(prefix + "URL");
        assertThat(url).as("Provide a dedicated test database via " + prefix + "URL").isNotBlank();
        var source = new DriverManagerDataSource(url, System.getenv(prefix + "USERNAME"), System.getenv(prefix + "PASSWORD"));
        var jdbc = new JdbcTemplate(source);
        // This table must not already exist; creation fails without modifying an existing table.
        jdbc.execute("CREATE TABLE moli_oauth2_authorized_client (authorization_key CHAR(64) PRIMARY KEY, token_data TEXT NOT NULL)");
        try {
            var registration = ClientRegistration.withRegistrationId("test").clientId("id").clientSecret("client-secret")
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).tokenUri("https://provider/token").build();
            var registrations = new InMemoryClientRegistrationRepository(registration);
            var first = new JdbcOAuth2AuthorizedClientService(jdbc, registrations);
            Instant issued = Instant.now().minusSeconds(10), expires = Instant.now().plusSeconds(3600);
            var original = new OAuth2AuthorizedClient(registration, "用户:" + UUID.randomUUID(),
                    new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access", issued, null, Set.of("scope")),
                    new OAuth2RefreshToken("refresh", issued, expires));
            first.saveAuthorizedClient(original, QuickOAuth2Client.principal(original.getPrincipalName()));
            var restarted = new JdbcOAuth2AuthorizedClientService(new JdbcTemplate(source), registrations);
            OAuth2AuthorizedClient loaded = restarted.loadAuthorizedClient("test", original.getPrincipalName());
            assertThat(loaded.getRefreshToken().getExpiresAt()).isEqualTo(expires);
            assertThat(loaded.getAccessToken().getExpiresAt()).isNull();
            var updated = new OAuth2AuthorizedClient(registration, original.getPrincipalName(),
                    new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "rotated", issued, expires, Set.of()), loaded.getRefreshToken());
            restarted.saveAuthorizedClient(updated, QuickOAuth2Client.principal(original.getPrincipalName()));
            assertThat(((OAuth2AuthorizedClient) first.loadAuthorizedClient("test", original.getPrincipalName())).getAccessToken().getTokenValue()).isEqualTo("rotated");
            assertThat(jdbc.queryForObject("SELECT token_data FROM moli_oauth2_authorized_client", String.class)).doesNotContain("client-secret");
            first.removeAuthorizedClient("test", original.getPrincipalName());
            assertThat((OAuth2AuthorizedClient) restarted.loadAuthorizedClient("test", original.getPrincipalName())).isNull();
        } finally { jdbc.execute("DROP TABLE moli_oauth2_authorized_client"); }
    }
}
