package com.bingchunmoli.oauth.client.redis;

import com.bingchunmoli.oauth.client.*;
import com.bingchunmoli.oauth.client.autoconfigure.QuickOAuth2Properties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@AutoConfiguration(afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@ConditionalOnProperty(prefix = "moli.oauth.client", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(QuickOAuth2Properties.class)
public class RedisOAuth2AutoConfiguration {
    @Bean @ConditionalOnMissingBean(RefreshCoordinator.class)
    @Conditional(SharedRefreshCondition.class)
    RedisRefreshCoordinator redisOAuth2RefreshCoordinator(StringRedisTemplate redis, QuickOAuth2Properties properties) {
        return new RedisRefreshCoordinator(redis, properties.getRefresh().getWaitTimeout(), properties.getRefresh().getLease(),
                properties.getRedis().getKeyPrefix());
    }
    @Bean @ConditionalOnMissingBean(OAuth2AuthorizedClientService.class)
    @ConditionalOnProperty(prefix = "moli.oauth.client.authorized-client", name = "store", havingValue = "redis")
    OAuth2AuthorizedClientService redisOAuth2AuthorizedClientService(StringRedisTemplate redis,
            ClientRegistrationRepository registrations, ObjectProvider<RefreshCoordinator> coordinators, QuickOAuth2Properties properties) {
        RefreshCoordinator coordinator = coordinators.getIfAvailable();
        return new RedisOAuth2AuthorizedClientService(redis, registrations,
                coordinator instanceof RedisRefreshCoordinator distributed ? distributed : null, properties.getRedis().getKeyPrefix());
    }

    static final class SharedRefreshCondition extends SpringBootCondition {
        @Override public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var environment = context.getEnvironment();
            String coordination = environment.getProperty("moli.oauth.client.refresh.coordination", "auto");
            String store = environment.getProperty("moli.oauth.client.authorized-client.store", "memory");
            boolean shared = "redis".equalsIgnoreCase(coordination) ||
                    ("auto".equalsIgnoreCase(coordination) && "redis".equalsIgnoreCase(store));
            return shared ? ConditionOutcome.match("Redis refresh coordination selected") :
                    ConditionOutcome.noMatch("Redis refresh coordination not selected");
        }
    }
}
