package com.bingchunmoli.oauth.server.integration;
import com.bingchunmoli.oauth.server.authorization.*;
import com.bingchunmoli.oauth.server.authorization.redis.*;
import com.bingchunmoli.oauth.server.authorization.jdbc.JdbcGrantCoordinator;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import static com.bingchunmoli.oauth.server.integration.ServerFixture.*;
import static org.assertj.core.api.Assertions.*;
class AuthorizationStorageIT {
    static String env(String key,String fallback){return System.getenv().getOrDefault(key,fallback);}
    @ParameterizedTest @ValueSource(strings={"mysql","postgresql","redis"})
    void durableAuthorizationConsentRestartAndMultiInstanceCompetition(String dialect) throws Exception {
        boolean sql=!dialect.equals("redis");
        String url=sql?env("OAUTH_TEST_"+(dialect.equals("mysql")?"MYSQL":"POSTGRESQL")+"_URL",""):null;
        Assumptions.assumeTrue(!sql || !url.isBlank(),"Real database URL required");
        Assumptions.assumeTrue(sql || System.getenv("OAUTH_TEST_REDIS_PORT")!=null,"Real Redis required");
        String user=sql?env("OAUTH_TEST_"+(dialect.equals("mysql")?"MYSQL":"POSTGRESQL")+"_USERNAME",dialect.equals("mysql")?"root":"oauth_test"):null;
        String password=sql?env("OAUTH_TEST_"+(dialect.equals("mysql")?"MYSQL":"POSTGRESQL")+"_PASSWORD",""):null;
        if(sql) {
            var source=new DriverManagerDataSource(url,user,password);
            String script=new String(new ClassPathResource("com/bingchunmoli/oauth/server/authorization/jdbc/schema-"+dialect+".sql").getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)
                    .replace("CREATE TABLE ","CREATE TABLE IF NOT EXISTS ");
            new ResourceDatabasePopulator(new ByteArrayResource(script.getBytes(java.nio.charset.StandardCharsets.UTF_8))).execute(source);
        }
        try(var fixture=new ServerFixture(sql?"jdbc":"redis",url,user,password,Integer.parseInt(env("OAUTH_TEST_REDIS_PORT","16379")))) {
            var browser=fixture.login();String verifier="a".repeat(64);
            var tokens=fixture.exchange(fixture.authorize(browser,"openid profile api.read",verifier),verifier);
            String refresh=tokens.path("refresh_token").asText();
            var clients=fixture.auth.getBean(RegisteredClientRepository.class);String clientId=clients.findByClientId(clientId("browser")).getId();
            assertThat(fixture.auth.getBean(OAuth2AuthorizationConsentService.class).findById(clientId,"alice").getScopes()).contains("api.read");
            String durable=fixture.machine("reference").path("access_token").asText();
            assertThat(fixture.api(fixture.opaqueBase,"/api/read",durable).statusCode()).as("Before restart introspection").isEqualTo(200);
            fixture.restartAuthorization();
            assertThat(fixture.auth.getBean(RegisteredClientRepository.class).findByClientId(clientId("browser")).getId()).isEqualTo(clientId);
            OAuth2Authorization persisted=fixture.auth.getBean(OAuth2AuthorizationService.class).findByToken(durable,OAuth2TokenType.ACCESS_TOKEN);
            assertThat(persisted).as("Recovered authorization").isNotNull();
            assertThat(persisted.getAccessToken().isActive()).as("Recovered access token active").isTrue();
            var introspected=post(browser(),fixture.authBase+"/oauth2/introspect",Map.of("token",durable),basic("machine"));
            assertThat(introspected.statusCode()).as(introspected.body()).isEqualTo(200);
            assertThat(fixture.json.readTree(introspected.body()).path("active").asBoolean()).as(introspected.body()).isTrue();
            var resourceResponse=fixture.api(fixture.opaqueBase,"/api/read",durable);
            assertThat(resourceResponse.statusCode()).as(resourceResponse.body()).isEqualTo(200);
            var recovered=post(browser(),fixture.authBase+"/oauth2/token",Map.of("grant_type","refresh_token","refresh_token",refresh),basic("browser"));
            assertThat(recovered.statusCode()).as(recovered.body()).isEqualTo(200);
            Map<String,String> secondProperties=new LinkedHashMap<>(fixture.authProperties);
            String secondBase="http://127.0.0.1:"+port();secondProperties.put("server.port",URI.create(secondBase).getPort()+"");
            try(ConfigurableApplicationContext second=boot(AuthHost.class,secondProperties)) {
                var loggedIn=fixture.login();String code=fixture.authorize(loggedIn,"api.read",verifier);
                List<HttpResponse<String>> codes=compete(fixture.authBase,secondBase,Map.of("grant_type","authorization_code","code",code,
                        "redirect_uri",fixture.clientBase+"/login/oauth2/code/local","code_verifier",verifier));
                assertOneSuccess(codes);
                var fresh=fixture.exchange(fixture.authorize(loggedIn,"api.read",verifier),verifier);
                List<HttpResponse<String>> refreshes=compete(fixture.authBase,secondBase,Map.of("grant_type","refresh_token","refresh_token",fresh.path("refresh_token").asText()));
                assertOneSuccess(refreshes);
                assertThat(refreshes.stream().filter(r->r.statusCode()==400).findFirst().orElseThrow().body()).contains("invalid_grant");
                OAuth2Authorization authorization=fixture.auth.getBean(OAuth2AuthorizationService.class).findByToken(durable,OAuth2TokenType.ACCESS_TOKEN);
                if(sql) jdbcTimeoutDoesNotExecuteConcurrentGrant(fixture,authorization.getId());
                else redisOwnershipTimeoutAndRenewal(fixture,authorization);
                var service=fixture.auth.getBean(OAuth2AuthorizationService.class);
                var expired=OAuth2Authorization.withRegisteredClient(fixture.auth.getBean(RegisteredClientRepository.class).findByClientId(clientId("machine")))
                        .principalName("expired-user").authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"expired-"+UUID.randomUUID(),Instant.now().minusSeconds(90),Instant.now().minusSeconds(60))).build();
                service.save(expired);
                if(service instanceof com.bingchunmoli.oauth.server.authorization.jdbc.JdbcExpiringAuthorizationService expiring) {
                    new JdbcTemplate(fixture.auth.getBean(javax.sql.DataSource.class)).update("update oauth2_authorization set attributes=? where id=?",
                            "unreadable-legacy-codec-data",expired.getId());
                    assertThatThrownBy(()->service.findById(expired.getId())).isInstanceOf(IllegalArgumentException.class);
                    expiring.cleanupExpired();assertThat(service.findById(expired.getId())).isNull();
                } else {
                    for(int attempt=0;attempt<20 && service.findById(expired.getId())!=null;attempt++)Thread.sleep(10);
                    assertThat(service.findById(expired.getId())).isNull();
                }
            }
            customAttributeCodecSurvivesRestart(fixture);
        }
    }
    static void customAttributeCodecSurvivesRestart(ServerFixture fixture) {
        fixture.authProperties.put("test.custom-codec","true");
        fixture.restartAuthorization();
        var clients=fixture.auth.getBean(RegisteredClientRepository.class);
        var identity=new TenantIdentity("tenant-a","alice");
        var authorization=OAuth2Authorization.withRegisteredClient(clients.findByClientId(clientId("machine")))
                .principalName("alice").authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .attribute("tenantIdentity",identity)
                .attribute(java.security.Principal.class.getName(),org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                        .authenticated(identity,null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_READER"))))
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"custom-"+UUID.randomUUID(),Instant.now(),Instant.now().plusSeconds(300)),
                        metadata->metadata.put("tenantIdentity",identity)).build();
        fixture.auth.getBean(OAuth2AuthorizationService.class).save(authorization);
        fixture.restartAuthorization();
        var recovered=fixture.auth.getBean(OAuth2AuthorizationService.class).findById(authorization.getId());
        assertThat(recovered.<TenantIdentity>getAttribute("tenantIdentity")).isEqualTo(identity);
        var principal=recovered.<org.springframework.security.core.Authentication>getAttribute(java.security.Principal.class.getName());
        assertThat(principal.getPrincipal()).isEqualTo(identity);
        assertThat(principal.getAuthorities()).extracting(org.springframework.security.core.GrantedAuthority::getAuthority).containsExactly("ROLE_READER");
        assertThat(recovered.getAccessToken().getMetadata().get("tenantIdentity")).isEqualTo(identity);
    }
    static List<HttpResponse<String>> compete(String first,String second,Map<String,String> form) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var one=pool.submit(()->{start.await();return post(browser(),first+"/oauth2/token",form,basic("browser"));});
            var two=pool.submit(()->{start.await();return post(browser(),second+"/oauth2/token",form,basic("browser"));});
            start.countDown();return List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
    }
    static void assertOneSuccess(List<HttpResponse<String>> responses) {
        assertThat(responses.stream().map(HttpResponse::statusCode).toList()).as(responses.stream().map(HttpResponse::body).toList().toString()).containsExactlyInAnyOrder(200,400);
    }
    static void jdbcTimeoutDoesNotExecuteConcurrentGrant(ServerFixture fixture,String id) throws Exception {
        var source=fixture.auth.getBean(javax.sql.DataSource.class);
        var first=new JdbcGrantCoordinator(source,Duration.ofSeconds(2));var second=new JdbcGrantCoordinator(source,Duration.ofMillis(100));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        try {
            Future<Integer> task=pool.submit(()->first.execute(id,()->{entered.countDown();try{release.await();}catch(InterruptedException e){throw new IllegalStateException(e);}return 1;}));
            assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->second.execute(id,()->{throw new AssertionError("Concurrent grant executed");})).isInstanceOf(OAuth2AuthenticationException.class);
            release.countDown();assertThat(task.get(3,TimeUnit.SECONDS)).isEqualTo(1);
        } finally {release.countDown();pool.shutdownNow();}
    }
    static void redisOwnershipTimeoutAndRenewal(ServerFixture fixture,OAuth2Authorization authorization) throws Exception {
        var redis=fixture.auth.getBean(StringRedisTemplate.class);String id=authorization.getId();
        concurrentClientIdsCannotCorruptIndexes(fixture,redis);
        var service=fixture.auth.getBean(OAuth2AuthorizationService.class);
        var coordinator=fixture.auth.getBean(RedisGrantCoordinator.class);
        String key=fixture.prefix+":lock:"+id;
        assertThatThrownBy(()->coordinator.execute(id,()->{
            redis.opsForValue().set(key,"another-owner",Duration.ofSeconds(3));service.save(authorization);return null;
        })).isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(redis.opsForValue().get(key)).isEqualTo("another-owner");
        redis.delete(key);
        try(var first=new RedisGrantCoordinator(redis,fixture.prefix,Duration.ofSeconds(2),Duration.ofMillis(300));
            var second=new RedisGrantCoordinator(redis,fixture.prefix,Duration.ofMillis(100),Duration.ofMillis(300))) {
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
            try {
                Future<Integer> task=pool.submit(()->first.execute(id,()->{entered.countDown();try{release.await();}catch(InterruptedException e){throw new IllegalStateException(e);}return 1;}));
                assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(()->second.execute(id,()->{throw new AssertionError("Parallel Redis grant");})).isInstanceOf(OAuth2AuthenticationException.class);
                Thread.sleep(450);assertThat(redis.opsForValue().get(key)).isNotNull();
                release.countDown();assertThat(task.get(3,TimeUnit.SECONDS)).isEqualTo(1);
            } finally {release.countDown();pool.shutdownNow();}
        }
        var restored=service.findById(id);assertThat(restored.getAccessToken().getToken().getTokenValue()).isEqualTo(authorization.getAccessToken().getToken().getTokenValue());
    }
    static void concurrentClientIdsCannotCorruptIndexes(ServerFixture fixture,StringRedisTemplate redis) throws Exception {
        var codec=fixture.auth.getBean(AuthorizationSnapshotCodec.class);
        var template=fixture.auth.getBean(RegisteredClientRepository.class).findByClientId(clientId("machine"));
        String id="concurrent-client-"+UUID.randomUUID();
        var arrived=new CountDownLatch(2);
        var repository=org.mockito.Mockito.spy(new RedisRegisteredClientRepository(redis,fixture.prefix,codec));
        org.mockito.Mockito.doAnswer(call->{
            arrived.countDown();assertThat(arrived.await(3,TimeUnit.SECONDS)).isTrue();return null;
        }).when(repository).findById(id);
        var pool=Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> saves=new ArrayList<>();
            for(String name:List.of(id+"-first",id+"-second")) saves.add(pool.submit(()->{
                try {repository.save(RegisteredClient.withId(id).clientId(name).clientSecret(template.getClientSecret())
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).scope("api.read").build());return true;}
                catch(IllegalStateException conflict){return false;}
            }));
            int succeeded=0;for(var saved:saves)if(saved.get(5,TimeUnit.SECONDS))succeeded++;
            assertThat(succeeded).as("Only one immutable client-id may claim an ID").isEqualTo(1);
            var persisted=new RedisRegisteredClientRepository(redis,fixture.prefix,codec);
            var winner=persisted.findById(id);
            assertThat(persisted.findByClientId(winner.getClientId()).getId()).isEqualTo(id);
            assertThat(persisted.saveIfAbsent(RegisteredClient.from(winner).clientName("replacement-must-not-win").build())).isFalse();
            assertThat(persisted.findById(id).getClientName()).isEqualTo(winner.getClientName());
            String loser=winner.getClientId().endsWith("-first")?id+"-second":id+"-first";
            assertThat(persisted.findByClientId(loser)).isNull();
        } finally {pool.shutdownNow();}
    }
}
