package com.bingchunmoli.oauth.client.apple;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.*;
import java.time.Instant;
import java.util.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

/** Generates short-lived secrets on the server for both code redemption and refresh. */
final class AppleClientSecrets {
    static final String ISSUER = "https://appleid.apple.com";
    private final AppleOAuth2Properties options;
    private volatile ECPrivateKey key;
    AppleClientSecrets(AppleOAuth2Properties options) { this.options = options; }
    private boolean generated() {
        return options.getPrivateKey() != null || options.getTeamId() != null || options.getKeyId() != null;
    }
    void validate(ClientRegistration registration) {
        if (generated()) {
            if (options.getTeamId() == null || !options.getTeamId().matches("[A-Za-z0-9]{10}") ||
                    options.getKeyId() == null || !options.getKeyId().matches("[A-Za-z0-9]{10}") || options.getPrivateKey() == null)
                throw new IllegalStateException("Apple signing requires team-id, key-id and a private-key resource");
            var ttl = options.getClientSecretTtl();
            if (ttl == null || ttl.getSeconds() < 1 || ttl.getSeconds() > 15777000 || ttl.getNano() != 0)
                throw new IllegalStateException("Apple client-secret-ttl must be whole seconds between 1 and 15777000");
            privateKey();
        } else {
            try {
                SignedJWT secret = SignedJWT.parse(registration.getClientSecret());
                var claims = secret.getJWTClaimsSet();
                if (!JWSAlgorithm.ES256.equals(secret.getHeader().getAlgorithm()) || secret.getHeader().getKeyID() == null ||
                        claims.getIssuer() == null || claims.getIssueTime() == null || claims.getExpirationTime() == null ||
                        !claims.getExpirationTime().toInstant().isAfter(Instant.now()) ||
                        claims.getExpirationTime().toInstant().isAfter(Instant.now().plusSeconds(15777000)) ||
                        !claims.getAudience().contains(ISSUER) || !registration.getClientId().equals(claims.getSubject()))
                    throw new IllegalArgumentException();
            } catch (Exception invalid) { throw new IllegalStateException("Configure Apple signing keys or a valid ES256 client-secret JWT"); }
        }
    }
    String secret(ClientRegistration registration) {
        validate(registration);
        if (!generated()) return registration.getClientSecret();
        Instant now = Instant.now();
        try {
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(options.getKeyId()).build(),
                    new JWTClaimsSet.Builder().issuer(options.getTeamId()).subject(registration.getClientId())
                            .audience(ISSUER).issueTime(Date.from(now))
                            .expirationTime(Date.from(now.plus(options.getClientSecretTtl()))).build());
            jwt.sign(new ECDSASigner(privateKey()));
            return jwt.serialize();
        } catch (JOSEException failure) { throw new IllegalStateException("Apple client secret signing failed"); }
    }
    private synchronized ECPrivateKey privateKey() {
        if (key != null) return key;
        try (var input = options.getPrivateKey().getInputStream()) {
            byte[] bytes = input.readNBytes(16385);
            if (bytes.length > 16384) throw new IllegalArgumentException();
            String pem = new String(bytes, StandardCharsets.US_ASCII)
                    .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            var parsed = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
            var parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            var expected = parameters.getParameterSpec(ECParameterSpec.class);
            if (!(parsed instanceof ECPrivateKey ec) || !ec.getParams().getCurve().equals(expected.getCurve()) ||
                    !ec.getParams().getGenerator().equals(expected.getGenerator()) || !ec.getParams().getOrder().equals(expected.getOrder()))
                throw new IllegalArgumentException();
            return key = ec;
        } catch (Exception failure) { throw new IllegalStateException("Apple private-key must be a readable P-256 PKCS#8 PEM key"); }
    }
}
