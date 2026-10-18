package com.bingchunmoli.oauth.client.redis;

import com.bingchunmoli.oauth.client.*;
import com.bingchunmoli.oauth.client.autoconfigure.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.assertj.core.api.Assertions.*;

class RedisOAuth2AutoConfigurationTest {
    ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                RedisOAuth2AutoConfiguration.class, QuickOAuth2AutoConfiguration.class))
                .withPropertyValues("moli.oauth.client.enabled=true",
                        "spring.security.oauth2.client.registration.app.client-id=id",
                        "spring.security.oauth2.client.registration.app.authorization-grant-type=client_credentials",
                        "spring.security.oauth2.client.registration.app.provider=custom",
                        "spring.security.oauth2.client.provider.custom.token-uri=https://provider/token");
    }
    @Test void redisStoreDefaultsToSharedCoordinatorAndNoConnectionIsOpenedAtStartup() {
        runner().withBean(StringRedisTemplate.class, () -> org.mockito.Mockito.mock(StringRedisTemplate.class))
                .withPropertyValues("moli.oauth.client.authorized-client.store=redis")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(RedisOAuth2AuthorizedClientService.class)
                        .hasSingleBean(RedisRefreshCoordinator.class));
    }
    @Test void redisModuleIsPassiveUntilSelectedAndExplicitSelectionRequiresConnection() {
        runner().run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(RedisRefreshCoordinator.class));
        runner().withPropertyValues("moli.oauth.client.refresh.coordination=redis")
                .run(context -> assertThat(context).hasFailed());
    }
    @ParameterizedTest
    @CsvSource({"Redis,auto", "REDIS,AUTO", "redis,REDIS", "Redis,LOCAL"})
    void configurationCaseCannotSilentlyChangeTheRefreshCoordinator(String store, String coordination) {
        runner().withBean(StringRedisTemplate.class, () -> org.mockito.Mockito.mock(StringRedisTemplate.class))
                .withPropertyValues("moli.oauth.client.authorized-client.store=" + store,
                        "moli.oauth.client.refresh.coordination=" + coordination)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(RedisOAuth2AuthorizedClientService.class);
                    if ("local".equalsIgnoreCase(coordination)) {
                        assertThat(context).hasSingleBean(LocalRefreshCoordinator.class).doesNotHaveBean(RedisRefreshCoordinator.class);
                    } else {
                        assertThat(context).hasSingleBean(RedisRefreshCoordinator.class).doesNotHaveBean(LocalRefreshCoordinator.class);
                    }
                });
    }
}
