package com.bingchunmoli.oauth.server.authorization.redis;
import com.bingchunmoli.oauth.server.authorization.*;
import com.bingchunmoli.oauth.server.authorization.autoconfigure.*;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
@AutoConfiguration(before=QuickAuthorizationServerAutoConfiguration.class,
    afterName="org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@ConditionalOnProperty(prefix="moli.oauth.auth-server",name="enabled",havingValue="true")
@ConditionalOnProperty(prefix="moli.oauth.auth-server.storage",name="type",havingValue="redis")
@EnableConfigurationProperties({QuickAuthorizationServerProperties.class,OAuth2AuthorizationServerProperties.class})
public class RedisAuthorizationServerAutoConfiguration {
    @Bean @ConditionalOnMissingBean
    AuthorizationSnapshotCodec authorizationSnapshotCodec(AuthorizationAttributeCodec attributes) {return new AuthorizationSnapshotCodec(attributes);}
    @Bean(destroyMethod="close") @ConditionalOnMissingBean(GrantCoordinator.class)
    RedisGrantCoordinator redisGrantCoordinator(StringRedisTemplate redis,QuickAuthorizationServerProperties quick) {
        var storage=quick.getStorage();return new RedisGrantCoordinator(redis,storage.getKeyPrefix(),storage.getWaitTimeout(),storage.getLease());
    }
    @Bean @ConditionalOnMissingBean(RegisteredClientRepository.class)
    RedisRegisteredClientRepository redisRegisteredClients(StringRedisTemplate redis,QuickAuthorizationServerProperties quick,AuthorizationSnapshotCodec codec) {
        return new RedisRegisteredClientRepository(redis,quick.getStorage().getKeyPrefix(),codec);
    }
    @Bean @ConditionalOnMissingBean(OAuth2AuthorizationService.class)
    OAuth2AuthorizationService redisAuthorizations(StringRedisTemplate redis,QuickAuthorizationServerProperties quick,AuthorizationSnapshotCodec codec,
            RegisteredClientRepository clients,RedisGrantCoordinator coordinator) {
        return new RedisAuthorizationService(redis,quick.getStorage().getKeyPrefix(),codec,clients,coordinator);
    }
    @Bean @ConditionalOnMissingBean(OAuth2AuthorizationConsentService.class)
    OAuth2AuthorizationConsentService redisConsents(StringRedisTemplate redis,QuickAuthorizationServerProperties quick,AuthorizationSnapshotCodec codec) {
        return new RedisAuthorizationConsentService(redis,quick.getStorage().getKeyPrefix(),codec);
    }
    @Bean @ConditionalOnBean(name="redisRegisteredClients")
    SmartInitializingSingleton initializeRedisOAuthClients(RedisRegisteredClientRepository clients,
            OAuth2AuthorizationServerProperties properties,Environment environment,QuickAuthorizationServerProperties quick) {
        return () -> {if(!properties.getClient().isEmpty())
            for(var client:QuickAuthorizationServerAutoConfiguration.configuredClients(properties,environment)) {
                QuickAuthorizationServerAutoConfiguration.audience(quick,client);
                if(clients.findByClientId(client.getClientId())==null) clients.saveIfAbsent(client);
            }
        };
    }
}
