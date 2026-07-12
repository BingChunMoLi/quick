package com.bingchunmoli.autoconfigure;

import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import com.bingchunmoli.filter.CacheFilter;
import com.bingchunmoli.interceptor.SignInterceptor;
import com.bingchunmoli.registrar.InterceptorsRegistrar;
import com.bingchunmoli.util.MD5SignUtil;
import com.bingchunmoli.util.SignUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InterceptorsAutoConfigurationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(InterceptorsAutoConfiguration.class))
            .withBean(ObjectMapper.class)
            .withBean(RedisUtil.class, () -> new RedisUtil(mock(RedisTemplate.class)));

    @Test
    void shouldNotCreateSignInfrastructureWhenSignIsDisabled() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(SignUtil.class);
            assertThat(context).doesNotHaveBean(SignInterceptor.class);
            assertThat(context).doesNotHaveBean(InterceptorsRegistrar.class);
            assertThat(context).doesNotHaveBean(CacheFilter.class);
        });
    }

    @Test
    void shouldAutoConfigureMd5SignInfrastructureWhenEnabledForBoot406() {
        contextRunner
                .withPropertyValues(
                        "moli.interceptor.sign.algorithm=MD5",
                        "moli.interceptor.sign.sign.enable=true",
                        "moli.interceptor.sign.sign.name=sign",
                        "moli.interceptor.sign.sign.parameter-position=QUERY"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(SignUtil.class);
                    assertThat(context).hasSingleBean(SignInterceptor.class);
                    assertThat(context).hasSingleBean(InterceptorsRegistrar.class);
                    assertThat(context).hasSingleBean(CacheFilter.class);
                    assertThat(context.getBean(SignUtil.class)).isInstanceOf(MD5SignUtil.class);
                });
    }
}
