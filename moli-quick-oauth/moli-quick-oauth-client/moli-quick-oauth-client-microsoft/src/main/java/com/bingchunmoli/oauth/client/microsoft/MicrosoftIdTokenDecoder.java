package com.bingchunmoli.oauth.client.microsoft;

import com.nimbusds.jose.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.*;
import com.nimbusds.jwt.JWTClaimsSet;
import java.security.Key;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.security.oauth2.client.oidc.authentication.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** Keeps native OIDC checks and also binds directory, token issuer and signing-key issuer. */
final class MicrosoftIdTokenDecoder {
    private MicrosoftIdTokenDecoder() { }
    static JwtDecoder create(ClientRegistration registration, MicrosoftOAuth2Properties options, RestClient http) {
        var policy = new Issuers(registration, options);
        var keys = new Keys(registration.getProviderDetails().getJwkSetUri(), policy, http, options.getJwkRefreshWaitTimeout().toNanos());
        var decoder = NimbusJwtDecoder.withJwkSetUri(registration.getProviderDetails().getJwkSetUri())
                .jwtProcessorCustomizer(processor -> processor.setJWTClaimsSetAwareJWSKeySelector(keys::select)).build();
        decoder.setClaimSetConverter(OidcIdTokenDecoderFactory.createDefaultClaimTypeConverter());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), token -> {
            try {
                policy.check(token.getIssuer().toString(), token.getClaimAsString("tid"));
                var resolved = ClientRegistration.withClientRegistration(registration).issuerUri(token.getIssuer().toString()).build();
                return new OidcIdTokenValidator(resolved).validate(token);
            } catch (RuntimeException invalid) { return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_id_token")); }
        }));
        return decoder;
    }
    private record Issuers(ClientRegistration registration, MicrosoftOAuth2Properties options) {
        void check(String issuer, String tenant) {
            if (!MicrosoftOAuth2ProviderAdapter.guid(tenant)) throw new IllegalArgumentException();
            String expected = registration.getProviderDetails().getIssuerUri().replace("{tenantid}", tenant);
            if (!expected.equals(issuer) || !issuer.equals(MicrosoftOAuth2ProviderAdapter.AUTHORITY + tenant + "/v2.0"))
                throw new IllegalArgumentException();
            String audience = options.getTenant();
            boolean personal = MicrosoftOAuth2ProviderAdapter.CONSUMER_TENANT.equalsIgnoreCase(tenant);
            if ("organizations".equals(audience) && personal || "consumers".equals(audience) && !personal ||
                    MicrosoftOAuth2ProviderAdapter.guid(audience) && !audience.equalsIgnoreCase(tenant))
                throw new IllegalArgumentException();
            if (!options.getAllowedTenants().isEmpty() && options.getAllowedTenants().stream().noneMatch(tenant::equalsIgnoreCase))
                throw new IllegalArgumentException();
        }
    }
    private record IssuedKey(JWK key, String issuer) { }
    private record Snapshot(List<IssuedKey> keys, long loadedAt) { }
    private static final class Keys {
        private final String uri;
        private final Issuers policy;
        private final RestClient http;
        private final JsonMapper json = JsonMapper.builder().build();
        private final ReentrantLock refreshLock = new ReentrantLock();
        private final long refreshWaitNanos;
        private volatile Snapshot snapshot;
        Keys(String uri, Issuers policy, RestClient http, long refreshWaitNanos) {
            this.uri = uri; this.policy = policy; this.http = http; this.refreshWaitNanos = refreshWaitNanos;
        }
        List<? extends Key> select(JWSHeader header, JWTClaimsSet claims, SecurityContext context) throws KeySourceException {
            try {
                if (!JWSAlgorithm.RS256.equals(header.getAlgorithm()) || header.getKeyID() == null) throw new IllegalArgumentException();
                String tenant = claims.getStringClaim("tid"), issuer = claims.getIssuer();
                policy.check(issuer, tenant);
                var current = snapshot;
                long now = System.nanoTime();
                boolean missing = current == null || current.keys().stream().noneMatch(key -> header.getKeyID().equals(key.key().getKeyID()));
                if (current == null || now - current.loadedAt() > 300_000_000_000L ||
                        missing && now - current.loadedAt() > 1_000_000_000L) current = refresh(current);
                List<JWK> eligible = current.keys().stream()
                        .filter(key -> key.issuer().replace("{tenantid}", tenant).equals(issuer))
                        .map(IssuedKey::key).toList();
                return new JWSVerificationKeySelector<SecurityContext>(JWSAlgorithm.RS256,
                        new ImmutableJWKSet<>(new JWKSet(eligible))).selectJWSKeys(header, context);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new KeySourceException("Microsoft signing key refresh interrupted");
            } catch (Exception failure) { throw new KeySourceException("Microsoft signing keys unavailable or invalid"); }
        }
        @SuppressWarnings("unchecked")
        private Snapshot refresh(Snapshot previous) throws java.text.ParseException, InterruptedException, KeySourceException {
            if (!refreshLock.tryLock(refreshWaitNanos, TimeUnit.NANOSECONDS))
                throw new KeySourceException("Microsoft signing key refresh wait timed out");
            try {
                if (snapshot != previous) return snapshot;
                String body = http.get().uri(uri).retrieve().body(String.class);
                if (body == null || body.length() > 1_000_000) throw new IllegalArgumentException();
                Map<String, Object> document = json.readValue(body, Map.class);
                if (!(document.get("keys") instanceof List<?> entries) || entries.size() > 100) throw new IllegalArgumentException();
                List<IssuedKey> result = new ArrayList<>();
                for (Object entry : entries) {
                    if (!(entry instanceof Map<?, ?> raw) || !(raw.get("issuer") instanceof String issuer)) throw new IllegalArgumentException();
                    JWK key = JWK.parse((Map<String, Object>) raw);
                    if (key.isPrivate()) throw new IllegalArgumentException();
                    result.add(new IssuedKey(key, issuer));
                }
                return snapshot = new Snapshot(List.copyOf(result), System.nanoTime());
            } finally { refreshLock.unlock(); }
        }
    }
}
