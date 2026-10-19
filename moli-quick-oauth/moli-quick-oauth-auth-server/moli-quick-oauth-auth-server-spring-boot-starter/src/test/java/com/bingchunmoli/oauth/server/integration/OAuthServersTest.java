package com.bingchunmoli.oauth.server.integration;
import java.util.*;
import java.net.URI;
import java.util.regex.*;
import java.net.http.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.*;
import com.nimbusds.jwt.SignedJWT;
import static org.assertj.core.api.Assertions.*;
import static com.bingchunmoli.oauth.server.integration.ServerFixture.*;
class OAuthServersTest {
    static ServerFixture servers;
    static final String VERIFIER="x".repeat(64);
    @BeforeAll static void start(){servers=new ServerFixture("memory",null,null,null,0);}
    @AfterAll static void stop(){if(servers!=null)servers.close();}
    @Test void machineJwtHasAudienceScopeAndStandardBearerErrors() throws Exception {
        var token=servers.machine("machine");String value=token.path("access_token").asText();
        assertThat(SignedJWT.parse(value).getJWTClaimsSet().getAudience()).containsExactly("quick-api");
        var response=servers.api(servers.resourceBase,"/api/read",value);assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains("SCOPE_api.read","ROLE_READER");
        var missing=servers.api(servers.resourceBase,"/api/read",null);assertThat(missing.statusCode()).isEqualTo(401);
        assertThat(missing.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(header->assertThat(header).startsWith("Bearer"));
        assertThat(servers.api(servers.resourceBase,"/api/admin",value).statusCode()).isEqualTo(403);
        assertThat(token.has("refresh_token")).isFalse();
        var query=get(browser(),servers.resourceBase+"/api/read?access_token="+encode(value));
        assertThat(query.statusCode()).isEqualTo(401);assertThat(query.headers().allValues("Set-Cookie")).isEmpty();
    }
    @Test void opaqueIntrospectionAndRevocationTakeEffectOnNextRequest() throws Exception {
        String token=servers.machine("reference").path("access_token").asText();
        assertThat(token.split("\\.")).hasSize(1);
        assertThat(servers.api(servers.opaqueBase,"/api/read",token).statusCode()).isEqualTo(200);
        var revoked=post(browser(),servers.authBase+"/oauth2/revoke",Map.of("token",token),basic("reference"));
        assertThat(revoked.statusCode()).isEqualTo(200);
        assertThat(servers.api(servers.opaqueBase,"/api/read",token).statusCode()).isEqualTo(401);
        assertThat(servers.api(servers.resourceBase,"/api/read",token).statusCode()).isEqualTo(401);
    }
    @Test void authorizationCodeOidcRefreshRotationAndReplay() throws Exception {
        var user=servers.login();String code=servers.authorize(user,"openid profile api.read",VERIFIER);
        var tokens=servers.exchange(code,VERIFIER);
        assertThat(tokens.path("id_token").asText()).isNotBlank();
        assertThat(servers.api(servers.resourceBase,"/api/read",tokens.path("id_token").asText()).statusCode()).isEqualTo(401);
        String oldRefresh=tokens.path("refresh_token").asText();assertThat(oldRefresh).isNotBlank();
        var userInfo=servers.api(servers.authBase,"/userinfo",tokens.path("access_token").asText());
        assertThat(userInfo.statusCode()).as(userInfo.body()).isEqualTo(200);assertThat(userInfo.body()).contains("alice");
        var refresh=post(browser(),servers.authBase+"/oauth2/token",Map.of("grant_type","refresh_token","refresh_token",oldRefresh),basic("browser"));
        assertThat(refresh.statusCode()).as(refresh.body()).isEqualTo(200);
        assertThat(servers.json.readTree(refresh.body()).path("refresh_token").asText()).isNotEqualTo(oldRefresh);
        var replay=post(browser(),servers.authBase+"/oauth2/token",Map.of("grant_type","refresh_token","refresh_token",oldRefresh),basic("browser"));
        assertThat(replay.statusCode()).isEqualTo(400);assertThat(replay.body()).contains("invalid_grant");
        var reused=post(browser(),servers.authBase+"/oauth2/token",Map.of("grant_type","authorization_code","code",code,
                "redirect_uri",servers.clientBase+"/login/oauth2/code/local","code_verifier",VERIFIER),basic("browser"));
        assertThat(reused.statusCode()).isEqualTo(400);
    }
    @Test void clientStarterCompletesRealOidcLoginAndAuthorizedApiCall() throws Exception {
        var user=servers.login();
        var start=get(user,servers.clientBase+"/oauth2/authorization/local");
        assertThat(start.statusCode()).isEqualTo(302);
        var authorize=get(user,servers.location(start));
        if(authorize.statusCode()==200) {
            Matcher state=Pattern.compile("name=\"state\"[^>]*value=\"([^\"]+)\"").matcher(authorize.body());assertThat(state.find()).isTrue();
            authorize=servers.consent(user,state.group(1),"profile api.read");
        }
        assertThat(authorize.statusCode()).as(authorize.body()).isEqualTo(302);
        var callback=get(user,servers.location(authorize));
        assertThat(callback.statusCode()).as(callback.body()).isEqualTo(200);assertThat(callback.body()).contains("\"login\":true");
        var api=get(user,servers.clientBase+"/api");assertThat(api.statusCode()).as(api.body()).isEqualTo(200);assertThat(api.body()).contains("alice");
        assertThat(get(user,servers.clientBase+"/who").body()).isEqualTo("anonymous");
    }
    @ParameterizedTest @ValueSource(strings={"wrong-audience","missing-audience","wrong-issuer","expired","wrong-signature"})
    void resourceRejectsInvalidJwtClaimsAndSignature(String mode) throws Exception {
        var claimsBuilder=new com.nimbusds.jwt.JWTClaimsSet.Builder().issuer(mode.equals("wrong-issuer")?"https://untrusted.test":servers.authBase)
                .subject("alice")
                .issueTime(new Date(System.currentTimeMillis()-120000))
                .expirationTime(new Date(System.currentTimeMillis()+(mode.equals("expired")?-120000:300000))).claim("scope","api.read");
        if(!mode.equals("missing-audience"))claimsBuilder.audience(mode.equals("wrong-audience")?"other-api":"quick-api");
        var jwt=new SignedJWT(new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(),claimsBuilder.build());
        jwt.sign(new com.nimbusds.jose.crypto.RSASSASigner(mode.equals("wrong-signature")?key():KEY));
        var response=servers.api(servers.resourceBase,"/api/read",jwt.serialize());
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(value->assertThat(value).contains("invalid_token"));
    }
    @Test void invalidPkceScopeAndCallbackAreRejected() throws Exception {
        var user=servers.login();String code=servers.authorize(user,"api.read",VERIFIER);
        var wrong=post(browser(),servers.authBase+"/oauth2/token",Map.of("grant_type","authorization_code","code",code,
                "redirect_uri",servers.clientBase+"/login/oauth2/code/local","code_verifier","z".repeat(64)),basic("browser"));
        assertThat(wrong.statusCode()).isEqualTo(400);
        var badScope=post(browser(),servers.authBase+"/oauth2/token",Map.of("grant_type","client_credentials","scope","root.admin"),basic("machine"));
        assertThat(badScope.statusCode()).isEqualTo(400);assertThat(badScope.body()).contains("invalid_scope");
        var badCallback=get(user,servers.authBase+"/oauth2/authorize?"+form(Map.of("response_type","code","client_id","browser",
                "redirect_uri","https://untrusted.test/callback","scope","api.read")));
        assertThat(badCallback.statusCode()).isEqualTo(400);assertThat(badCallback.headers().firstValue("Location")).isEmpty();
    }
    @Test void jwtRevocationDoesNotPretendToInvalidateLocalSignature() throws Exception {
        String token=servers.machine("machine").path("access_token").asText();
        assertThat(post(browser(),servers.authBase+"/oauth2/revoke",Map.of("token",token),basic("machine")).statusCode()).isEqualTo(200);
        assertThat(servers.api(servers.resourceBase,"/api/read",token).statusCode()).isEqualTo(200);
        assertThat(servers.api(servers.opaqueBase,"/api/read",token).statusCode()).isEqualTo(401);
    }
    @Test void discoveryAndJwksPublishNoPrivateSigningMaterial() throws Exception {
        var discovery=get(browser(),servers.authBase+"/.well-known/openid-configuration");assertThat(discovery.statusCode()).isEqualTo(200);
        assertThat(servers.json.readTree(discovery.body()).path("issuer").asText()).isEqualTo(servers.authBase);
        var keys=get(browser(),servers.authBase+"/oauth2/jwks");assertThat(keys.statusCode()).isEqualTo(200);
        assertThat(keys.body()).contains("server-test-key").doesNotContain("\"d\"", "\"p\"", "\"q\"");
    }
    @Test void expiredAuthorizationCodeAndWrongClientSecretAreRejected() throws Exception {
        var clients=servers.auth.getBean(org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository.class);
        var service=servers.auth.getBean(org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService.class);
        String code="expired-"+UUID.randomUUID();
        var authorization=org.springframework.security.oauth2.server.authorization.OAuth2Authorization.withRegisteredClient(clients.findByClientId(clientId("browser")))
                .principalName("alice").authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(new org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode(code,java.time.Instant.now().minusSeconds(90),java.time.Instant.now().minusSeconds(60))).build();
        service.save(authorization);
        var expired=post(browser(),servers.authBase+"/oauth2/token",Map.of("grant_type","authorization_code","code",code,
                "redirect_uri",servers.clientBase+"/login/oauth2/code/local","code_verifier",VERIFIER),basic("browser"));
        assertThat(expired.statusCode()).isEqualTo(400);assertThat(expired.body()).contains("invalid_grant").doesNotContain(code);
        String wrong="Basic "+Base64.getEncoder().encodeToString((clientId("machine")+":private-wrong-client-secret").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var failed=post(browser(),servers.authBase+"/oauth2/token",Map.of("grant_type","client_credentials"),wrong);
        assertThat(failed.statusCode()).isEqualTo(401);assertThat(failed.body()).contains("invalid_client").doesNotContain("private-wrong-client-secret");
    }
}
