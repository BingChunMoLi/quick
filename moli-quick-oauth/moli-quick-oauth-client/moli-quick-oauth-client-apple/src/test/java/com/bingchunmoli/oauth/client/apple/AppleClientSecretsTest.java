package com.bingchunmoli.oauth.client.apple;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jwt.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import static org.assertj.core.api.Assertions.*;

class AppleClientSecretsTest {
    static ClientRegistration registration(String secret) {
        return ClientRegistration.withRegistrationId("apple").clientId("app").clientSecret(secret)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("https://app.test/callback")
                .authorizationUri("https://appleid.apple.com/auth/authorize").tokenUri("https://appleid.apple.com/auth/token").build();
    }
    static String secret(String scenario) throws Exception {
        var key = new ECKeyGenerator(Curve.P_256).generate();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID("KEY1234567").build(),
                new JWTClaimsSet.Builder().issuer("TEAM123456").subject("subject".equals(scenario) ? "another-app" : "app")
                        .audience("audience".equals(scenario) ? "https://untrusted.test" : AppleClientSecrets.ISSUER)
                        .issueTime(Date.from(Instant.now().minusSeconds(60)))
                        .expirationTime(Date.from("expired".equals(scenario) ? Instant.now().minusSeconds(10) : Instant.now().plusSeconds(120))).build());
        jwt.sign(new ECDSASigner(key)); return jwt.serialize();
    }
    @Test void configuredClientSecretJwtIsAcceptedWithoutPrivateKey() throws Exception {
        String encoded = secret("ok");
        assertThat(new AppleClientSecrets(new AppleOAuth2Properties()).secret(registration(encoded))).isEqualTo(encoded);
    }
    @ParameterizedTest @ValueSource(strings = {"expired", "audience", "subject"})
    void invalidConfiguredSecretIsRejected(String scenario) throws Exception {
        var registration = registration(secret(scenario));
        assertThatThrownBy(() -> new AppleClientSecrets(new AppleOAuth2Properties()).validate(registration))
                .isInstanceOf(IllegalStateException.class).hasMessage("Configure Apple signing keys or a valid ES256 client-secret JWT");
    }
    @ParameterizedTest @ValueSource(strings = {"PT0S", "PT-1S", "PT0.001S", "PT15777001S"})
    void invalidGeneratedSecretLifetimeFailsBeforeUse(String duration) {
        var options = new AppleOAuth2Properties(); options.setTeamId("TEAM123456"); options.setKeyId("KEY1234567");
        options.setPrivateKey(new ByteArrayResource(new byte[0])); options.setClientSecretTtl(Duration.parse(duration));
        assertThatThrownBy(() -> new AppleClientSecrets(options).validate(registration("")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("client-secret-ttl");
    }
    @Test void generatedSecretRejectsWrongCurve() throws Exception {
        var key = new ECKeyGenerator(Curve.P_384).generate();
        String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(key.toECPrivateKey().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        var options = new AppleOAuth2Properties(); options.setTeamId("TEAM123456"); options.setKeyId("KEY1234567");
        options.setPrivateKey(new ByteArrayResource(pem.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        assertThatThrownBy(() -> new AppleClientSecrets(options).validate(registration("")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("P-256");
    }
}
