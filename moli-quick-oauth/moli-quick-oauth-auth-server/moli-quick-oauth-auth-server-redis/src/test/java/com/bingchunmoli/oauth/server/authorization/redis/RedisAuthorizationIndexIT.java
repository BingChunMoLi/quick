package com.bingchunmoli.oauth.server.authorization.redis;

import com.bingchunmoli.oauth.server.authorization.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import static org.assertj.core.api.Assertions.*;

class RedisAuthorizationIndexIT {
    @ParameterizedTest @ValueSource(strings={"remove","replace","invalidate"})
    void expiredIndexesReassignedToAnotherAuthorizationArePreserved(String operation) throws Exception {
        String port=System.getenv("OAUTH_TEST_REDIS_PORT");Assumptions.assumeTrue(port!=null,"Real Redis required");
        var connection=new LettuceConnectionFactory("127.0.0.1",Integer.parseInt(port));connection.afterPropertiesSet();connection.start();
        var redis=new StringRedisTemplate(connection);String prefix="test:quick:authorization-index:"+UUID.randomUUID();
        var client=RegisteredClient.withId("client").clientId("client").clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).scope("read").build();
        String recycled="recycled-"+UUID.randomUUID(),replacement="replacement-"+UUID.randomUUID(),refresh="refresh-"+UUID.randomUUID();
        try(var coordinator=new RedisGrantCoordinator(redis,prefix,Duration.ofSeconds(1),Duration.ofSeconds(3))) {
            var service=new RedisAuthorizationService(redis,prefix,new AuthorizationSnapshotCodec(new SecurityAuthorizationAttributeCodec()),
                    new InMemoryRegisteredClientRepository(client),coordinator);
            var first=OAuth2Authorization.withRegisteredClient(client).id("first").principalName("alice")
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,recycled,Instant.now(),Instant.now().plusMillis(250)))
                    .refreshToken(new OAuth2RefreshToken(refresh,Instant.now(),Instant.now().plusSeconds(3600))).build();
            service.save(first);
            String index=prefix+":token:"+AuthorizationSnapshotCodec.digest(recycled);
            long deadline=System.nanoTime()+Duration.ofSeconds(3).toNanos();
            while(Boolean.TRUE.equals(redis.hasKey(index)) && System.nanoTime()<deadline)Thread.sleep(20);
            assertThat(redis.hasKey(index)).isFalse();
            var second=OAuth2Authorization.withRegisteredClient(client).id("second").principalName("bob")
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,recycled,Instant.now(),Instant.now().plusSeconds(300))).build();
            service.save(second);
            assertThat(service.findByToken(recycled,OAuth2TokenType.ACCESS_TOKEN).getId()).isEqualTo("second");
            switch(operation) {
                case "remove" -> service.remove(first);
                case "replace" -> service.save(OAuth2Authorization.from(first)
                        .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,replacement,Instant.now(),Instant.now().plusSeconds(300))).build());
                default -> service.save(OAuth2Authorization.from(first).invalidate(first.getRefreshToken().getToken()).build());
            }
            var recovered=service.findByToken(recycled,OAuth2TokenType.ACCESS_TOKEN);
            assertThat(recovered).as("Updating old authorization must preserve the new index owner").isNotNull();
            assertThat(recovered.getId()).isEqualTo("second");
            assertThat(service.findById("second").getPrincipalName()).isEqualTo("bob");
        } finally {
            try {
                redis.delete(List.of(prefix+":authorization:first",prefix+":authorization:first:indexes",prefix+":authorization:second",
                        prefix+":authorization:second:indexes",prefix+":lock:first",prefix+":lock:second",
                        prefix+":token:"+AuthorizationSnapshotCodec.digest(recycled),prefix+":token:"+AuthorizationSnapshotCodec.digest(replacement),
                        prefix+":token:"+AuthorizationSnapshotCodec.digest(refresh)));
            } finally {connection.destroy();}
        }
    }
}
