package com.bingchunmoli.oauth.client.apple;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AppleTokenRequestTest {
    private static final String TOKEN_URI = "https://appleid.apple.com/auth/token";
    private static final String REDIRECT_URI = "https://app.test/login/oauth2/code/apple";
    private static ECKey signingKey;
    private static ByteArrayResource privateKey;

    @BeforeAll static void signingKey() throws Exception {
        signingKey = new ECKeyGenerator(Curve.P_256).keyID("KEY1234567").generate();
        String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(signingKey.toECPrivateKey().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        privateKey = new ByteArrayResource(pem.getBytes(StandardCharsets.US_ASCII));
    }

    static Stream<Arguments> tokenRequests() {
        return Stream.of("generated", "generated-with-old-secret", "static")
                .flatMap(mode -> Stream.of("authorization_code", "refresh_token").map(grant -> Arguments.of(mode, grant)));
    }

    @ParameterizedTest(name = "{0}: {1}") @MethodSource("tokenRequests")
    void tokenRequestContainsOneValidClientSecretAndRetainsGrantParameters(String mode, String grant) throws Exception {
        var properties = new AppleOAuth2Properties();
        String configuredSecret = "";
        if ("static".equals(mode)) {
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID("KEY1234567").build(),
                    new JWTClaimsSet.Builder().issuer("TEAM123456").subject("app").audience(AppleClientSecrets.ISSUER)
                            .issueTime(Date.from(Instant.now().minusSeconds(60)))
                            .expirationTime(Date.from(Instant.now().plusSeconds(300))).build());
            jwt.sign(new ECDSASigner(signingKey));
            configuredSecret = jwt.serialize();
        } else {
            properties.setTeamId("TEAM123456"); properties.setKeyId("KEY1234567"); properties.setPrivateKey(privateKey);
            if ("generated-with-old-secret".equals(mode)) configuredSecret = "old-secret";
        }
        String expectedStaticSecret = configuredSecret;
        var registration = ClientRegistration.withRegistrationId("apple").clientId("app").clientSecret(configuredSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri(REDIRECT_URI)
                .scope("openid", "name", "email").authorizationUri("https://appleid.apple.com/auth/authorize").tokenUri(TOKEN_URI)
                .issuerUri(AppleClientSecrets.ISSUER).jwkSetUri("https://appleid.apple.com/auth/keys").build();
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var adapter = new AppleOAuth2ProviderAdapter(properties, builder);
        server.expect(requestTo(TOKEN_URI)).andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(request -> {
                    var parameters = new LinkedMultiValueMap<String, String>();
                    for (String part : ((MockClientHttpRequest) request).getBodyAsString().split("&")) {
                        String[] pair = part.split("=", 2);
                        parameters.add(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                                URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8));
                    }
                    parameters.forEach((name, values) -> assertThat(values.size()).as("Token request parameter %s count", name).isEqualTo(1));
                    assertThat(parameters.getFirst("client_id")).isEqualTo("app");
                    assertThat(parameters.getFirst("grant_type")).isEqualTo(grant);
                    String encoded = parameters.getFirst("client_secret");
                    assertThat(encoded).isNotBlank();
                    try {
                        var secret = SignedJWT.parse(encoded);
                        assertThat(secret.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
                        assertThat(secret.getHeader().getKeyID()).isEqualTo("KEY1234567");
                        assertThat(secret.verify(new ECDSAVerifier(signingKey.toECPublicKey()))).isTrue();
                        var claims = secret.getJWTClaimsSet();
                        assertThat(claims.getIssuer()).isEqualTo("TEAM123456");
                        assertThat(claims.getSubject()).isEqualTo("app");
                        assertThat(claims.getAudience()).containsExactly(AppleClientSecrets.ISSUER);
                        assertThat(claims.getExpirationTime().toInstant()).isAfter(Instant.now());
                    } catch (Exception failure) { throw new AssertionError("Expected a valid signed Apple client secret", failure); }
                    if ("static".equals(mode)) assertThat(encoded).isEqualTo(expectedStaticSecret);
                    if ("authorization_code".equals(grant)) {
                        assertThat(parameters.getFirst("code")).isEqualTo("test-code");
                        assertThat(parameters.getFirst("redirect_uri")).isEqualTo(REDIRECT_URI);
                        assertThat(parameters.getFirst("code_verifier")).isEqualTo("test-verifier");
                    } else {
                        assertThat(parameters.getFirst("refresh_token")).isEqualTo("test-refresh");
                        assertThat(parameters.getFirst("scope")).isEqualTo("email");
                    }
                }).andRespond(withSuccess("{\"access_token\":\"test-access\",\"token_type\":\"Bearer\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
        OAuth2AccessTokenResponse response;
        if ("authorization_code".equals(grant)) {
            var authorization = OAuth2AuthorizationRequest.authorizationCode().authorizationUri(registration.getProviderDetails().getAuthorizationUri())
                    .clientId("app").redirectUri(REDIRECT_URI).state("test-state")
                    .attributes(values -> values.put("code_verifier", "test-verifier")).build();
            var callback = OAuth2AuthorizationResponse.success("test-code").redirectUri(REDIRECT_URI).state("test-state").build();
            response = adapter.authorizationCodeClient().getTokenResponse(new OAuth2AuthorizationCodeGrantRequest(registration,
                    new OAuth2AuthorizationExchange(authorization, callback)));
        } else {
            response = adapter.refreshTokenClient().getTokenResponse(new OAuth2RefreshTokenGrantRequest(registration,
                    new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "expired", Instant.now().minusSeconds(120), Instant.now().minusSeconds(60)),
                    new OAuth2RefreshToken("test-refresh", Instant.now()), Set.of("email")));
        }
        assertThat(response.getAccessToken().getTokenValue()).isEqualTo("test-access");
        server.verify();
    }
}
