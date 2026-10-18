package com.bingchunmoli.oauth.client.redis;

import com.bingchunmoli.oauth.client.autoconfigure.QuickOAuth2Properties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

@AutoConfiguration(after = RedisOAuth2AutoConfiguration.class)
@ConditionalOnClass(HttpServletRequest.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "moli.oauth.client", name = {"enabled", "login.enabled"}, havingValue = "true")
@EnableConfigurationProperties(QuickOAuth2Properties.class)
public class RedisOAuth2WebAutoConfiguration {
    @Bean @ConditionalOnMissingBean(AuthorizationRequestRepository.class)
    @ConditionalOnProperty(prefix = "moli.oauth.client.authorization-state", name = "store", havingValue = "redis")
    AuthorizationRequestRepository<OAuth2AuthorizationRequest> redisOAuth2AuthorizationRequests(
            StringRedisTemplate redis, QuickOAuth2Properties properties) {
        return new RedisAuthorizationRequestRepository(redis, properties.getAuthorizationState().getTtl(),
                properties.getAuthorizationState().isCookieSecure(), properties.getRedis().getKeyPrefix());
    }
}
