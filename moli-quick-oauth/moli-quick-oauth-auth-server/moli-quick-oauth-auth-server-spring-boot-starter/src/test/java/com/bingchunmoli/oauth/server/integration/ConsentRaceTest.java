package com.bingchunmoli.oauth.server.integration;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.bingchunmoli.oauth.server.integration.ServerFixture.*;

class ConsentRaceTest {
    @Test void duplicateConsentCannotIssueTwoCodesOrOverwriteIssuedAuthorization() throws Exception {
        try(var fixture=new ServerFixture("memory",null,null,null,0)) {
            fixture.authProperties.put("test.consent-race","true");fixture.restartAuthorization();
            var browser=fixture.login();String verifier="c".repeat(64);
            String consentState=beginConsent(fixture,browser,verifier);fixture.auth.getBean(ConsentBarrierService.class).arm(consentState);
            var pool=Executors.newFixedThreadPool(2);
            try {
                var first=pool.submit(()->fixture.consent(browser,consentState,"api.read"));
                var second=pool.submit(()->fixture.consent(browser,consentState,"api.read"));
                var responses=List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));
                var codes=responses.stream().filter(response->response.statusCode()==302)
                        .map(response->MockQuery.parse(URI.create(fixture.location(response)).getRawQuery()).get("code"))
                        .filter(Objects::nonNull).toList();
                assertThat(codes.size()).as("A consent state may issue only one code").isEqualTo(1);
                assertThat(responses.stream().map(response->response.statusCode()).toList()).containsExactlyInAnyOrder(302,400);
                String token=fixture.exchange(codes.get(0),verifier).path("access_token").asText();
                assertThat(fixture.consent(browser,consentState,"api.read").statusCode()).isEqualTo(400);
                assertThat(fixture.api(fixture.opaqueBase,"/api/read",token).statusCode()).isEqualTo(200);
            } finally {pool.shutdownNow();}
        }
    }
    @Test void coordinationTimeoutReturnsProtocolFailureAndDoesNotConsumeConsent() throws Exception {
        try(var fixture=new ServerFixture("memory",null,null,null,0)) {
            fixture.authProperties.put("test.consent-timeout","true");
            fixture.restartAuthorization();
            var browser=fixture.login();String state=beginConsent(fixture,browser,"t".repeat(64));
            var response=fixture.consent(browser,state,"api.read");
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).doesNotContain("ClassCastException");
            assertThat(fixture.auth.getBean(org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService.class)
                    .findByToken(state,new org.springframework.security.oauth2.server.authorization.OAuth2TokenType("state"))).isNotNull();
        }
    }
    private static String beginConsent(ServerFixture fixture,java.net.http.HttpClient browser,String verifier) throws Exception {
        String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        var page=get(browser,fixture.authBase+"/oauth2/authorize?"+form(Map.of("response_type","code","client_id",clientId("browser"),
                "redirect_uri",fixture.clientBase+"/login/oauth2/code/local","scope","api.read","state","browser-state",
                "code_challenge",challenge,"code_challenge_method","S256")));
        assertThat(page.statusCode()).isEqualTo(200);
        var state=Pattern.compile("name=\"state\"[^>]*value=\"([^\"]+)\"").matcher(page.body());assertThat(state.find()).isTrue();
        return state.group(1);
    }
}
