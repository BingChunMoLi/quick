package com.bingchunmoli.oauth.server.integration;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.*;
import static org.assertj.core.api.Assertions.*;
import static com.bingchunmoli.oauth.server.integration.ServerFixture.*;

class UserInfoTokenTest {
    static final String FORMAT="spring.security.oauth2.authorizationserver.client.browser.token.access-token-format";
    static final String VERIFIER="u".repeat(64);
    static ServerFixture servers;

    @BeforeAll static void start(){servers=new ServerFixture("memory",null,null,null,0,Map.of(FORMAT,"reference"));}
    @AfterAll static void stop(){if(servers!=null)servers.close();}

    @Test void opaqueAccessTokenSupportsUserInfoGetAndPost() throws Exception {
        var tokens=servers.exchange(servers.authorize(servers.login(),"openid profile api.read",VERIFIER),VERIFIER);
        String access=tokens.path("access_token").asText();
        assertThat(access.split("\\.")).hasSize(1);
        assertThat(tokens.path("id_token").asText()).isNotBlank();
        assertThat(servers.api(servers.authBase,"/userinfo",access).statusCode()).isEqualTo(200);
        var response=post(browser(),servers.authBase+"/userinfo",Map.of(),"Bearer "+access);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(servers.json.readTree(response.body()).path("sub").asText()).isEqualTo("alice");
    }

    @Test void opaqueClientCompletesOidcLoginAndAuthorizedApiCall() throws Exception {
        loginAndCallApi(servers);
    }

    @Test void customUserInfoEndpointAndContextPathSupportOpaqueOidcLogin() throws Exception {
        try(var custom=new ServerFixture("memory",null,null,null,0,Map.of(FORMAT,"reference",
                "server.servlet.context-path","/auth",
                "spring.security.oauth2.authorizationserver.endpoint.oidc.user-info-uri","/oidc/profile"))) {
            var discovery=get(browser(),custom.authBase+"/.well-known/openid-configuration");
            assertThat(custom.json.readTree(discovery.body()).path("userinfo_endpoint").asText()).isEqualTo(custom.authBase+"/oidc/profile");
            var tokens=custom.exchange(custom.authorize(custom.login(),"openid profile api.read",VERIFIER),VERIFIER);
            var response=custom.api(custom.authBase,"/oidc/profile",tokens.path("access_token").asText());
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            loginAndCallApi(custom);
        }
    }

    @ParameterizedTest @ValueSource(strings={"self-contained","reference"})
    void refreshAndRevocationKeepUserInfoTokenStateAuthoritative(String format) throws Exception {
        var clients=servers.auth.getBean(RegisteredClientRepository.class);
        var original=clients.findByClientId(clientId("browser"));
        clients.save(RegisteredClient.from(original).tokenSettings(TokenSettings.withSettings(original.getTokenSettings().getSettings())
                .accessTokenFormat(new OAuth2TokenFormat(format)).build()).build());
        try {
            var tokens=servers.exchange(servers.authorize(servers.login(),"openid profile api.read",VERIFIER),VERIFIER);
            assertThat(servers.api(servers.authBase,"/userinfo",tokens.path("access_token").asText()).statusCode()).isEqualTo(200);
            var refresh=post(browser(),servers.authBase+"/oauth2/token",Map.of("grant_type","refresh_token",
                    "refresh_token",tokens.path("refresh_token").asText()),basic("browser"));
            assertThat(refresh.statusCode()).as(refresh.body()).isEqualTo(200);
            String access=servers.json.readTree(refresh.body()).path("access_token").asText();
            assertThat(servers.api(servers.authBase,"/userinfo",access).statusCode()).isEqualTo(200);
            assertThat(post(browser(),servers.authBase+"/oauth2/revoke",Map.of("token",access),basic("browser")).statusCode()).isEqualTo(200);
            assertInvalid(access);
        } finally {clients.save(original);}
    }

    @ParameterizedTest @ValueSource(strings={"expired","future","removed"})
    void inactiveStoredAccessTokensAreRejected(String state) throws Exception {
        var tokens=servers.exchange(servers.authorize(servers.login(),"openid profile api.read",VERIFIER),VERIFIER);
        String access=tokens.path("access_token").asText();
        var service=servers.auth.getBean(OAuth2AuthorizationService.class);
        var authorization=service.findByToken(access,OAuth2TokenType.ACCESS_TOKEN);
        if(state.equals("removed")) service.remove(authorization);
        else if(state.equals("expired")) service.save(OAuth2Authorization.from(authorization).token(new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,access,Instant.now().minusSeconds(120),Instant.now().minusSeconds(60),
                authorization.getAccessToken().getToken().getScopes())).build());
        else service.save(OAuth2Authorization.from(authorization).token(authorization.getAccessToken().getToken(),metadata->{
            var claims=new LinkedHashMap<>(authorization.getAccessToken().getClaims());
            claims.put("nbf",Instant.now().plusSeconds(300));metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME,claims);
        }).build());
        assertInvalid(access);
    }

    @Test void refreshIdAndUnknownTokensCannotActAsAccessTokens() throws Exception {
        var tokens=servers.exchange(servers.authorize(servers.login(),"openid profile api.read",VERIFIER),VERIFIER);
        for(String value:List.of(tokens.path("refresh_token").asText(),tokens.path("id_token").asText(),
                "unknown-userinfo-token",tokens.path("access_token").asText()+"tampered")) assertInvalid(value);
    }

    @Test void accessTokenStillRequiresOpenidScope() throws Exception {
        String access=servers.exchange(servers.authorize(servers.login(),"api.read",VERIFIER),VERIFIER).path("access_token").asText();
        var response=servers.api(servers.authBase,"/userinfo",access);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("insufficient_scope");
        assertThat(servers.api(servers.authBase,"/userinfo",servers.machine("reference").path("access_token").asText()).statusCode()).isEqualTo(403);
    }

    @Test void openidAccessTokenStillRequiresAnAssociatedIdToken() throws Exception {
        var client=servers.auth.getBean(RegisteredClientRepository.class).findByClientId(clientId("browser"));
        String value="openid-without-id-token-"+UUID.randomUUID();
        var authorization=OAuth2Authorization.withRegisteredClient(client).principalName("alice")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .accessToken(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,value,Instant.now(),Instant.now().plusSeconds(300),Set.of("openid"))).build();
        servers.auth.getBean(OAuth2AuthorizationService.class).save(authorization);
        var response=servers.api(servers.authBase,"/userinfo",value);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("invalid_token").doesNotContain(value);
    }

    @Test void loginSessionDoesNotReplaceUserInfoBearerAuthentication() throws Exception {
        HttpClient user=servers.login();
        assertThat(get(user,servers.authBase+"/userinfo").statusCode()).isEqualTo(401);
        var response=post(user,servers.authBase+"/userinfo",Map.of(),"Bearer unknown-userinfo-token");
        assertThat(response.statusCode()).isEqualTo(401);
    }

    private static void assertInvalid(String token) throws Exception {
        var response=servers.api(servers.authBase,"/userinfo",token);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(value->assertThat(value).contains("invalid_token"));
        assertThat(response.body()+response.headers().map()).doesNotContain(token);
    }

    private static void loginAndCallApi(ServerFixture fixture) throws Exception {
        var browser=fixture.login();
        var start=get(browser,fixture.clientBase+"/oauth2/authorization/local");
        assertThat(start.statusCode()).isEqualTo(302);
        var authorize=get(browser,fixture.location(start));
        if(authorize.statusCode()==200) {
            var state=Pattern.compile("name=\"state\"[^>]*value=\"([^\"]+)\"").matcher(authorize.body());assertThat(state.find()).isTrue();
            authorize=fixture.consent(browser,state.group(1),"profile api.read");
        }
        assertThat(authorize.statusCode()).as(authorize.body()).isEqualTo(302);
        var callback=get(browser,fixture.location(authorize));
        assertThat(callback.statusCode()).as(callback.body()).isEqualTo(200);
        assertThat(callback.body()).contains("\"login\":true");
        var api=get(browser,fixture.clientBase+"/api");
        assertThat(api.statusCode()).as(api.body()).isEqualTo(200);assertThat(api.body()).contains("alice");
    }
}
