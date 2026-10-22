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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

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

    @Test
    void enabledSignaturesDoNotRequireAHostJackson2Mapper() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(InterceptorsAutoConfiguration.class))
                .withBean(RedisUtil.class, () -> new RedisUtil(mock(RedisTemplate.class)))
                .withPropertyValues("moli.interceptor.sign.algorithm=MD5", "moli.interceptor.sign.sign.enable=true")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(SignUtil.class);
                    assertThat(context).doesNotHaveBean(ObjectMapper.class);
                });
    }

    @Test
    void hostSignUtilIsTheImplementationUsedByTheInterceptor() {
        SignUtil custom = mock(SignUtil.class);
        contextRunner.withPropertyValues("moli.interceptor.sign.sign.enable=true")
                .withBean("hostSignUtil", SignUtil.class, () -> custom).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(SignUtil.class);
                    when(custom.verify(any())).thenReturn(true);
                    assertThat(context.getBean(SignInterceptor.class).preHandle(
                            new MockHttpServletRequest("GET", "/signed"), new MockHttpServletResponse(), new Object())).isTrue();
                    verify(custom).verify(any());
                });
    }

    @Test
    void hostInterceptorAndFilterAreUsedWithoutDuplicateBeans() {
        SignInterceptor custom = mock(SignInterceptor.class);
        CacheFilter filter = new CacheFilter();
        contextRunner.withPropertyValues("moli.interceptor.sign.sign.enable=true", "moli.interceptor.sign.algorithm=MD5")
                .withBean(SignInterceptor.class, () -> custom).withBean(CacheFilter.class, () -> filter).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(SignInterceptor.class).hasSingleBean(CacheFilter.class);
                    assertThat(ReflectionTestUtils.getField(context.getBean(InterceptorsRegistrar.class), "signInterceptor"))
                            .isSameAs(custom);
                    assertThat(context.getBean(CacheFilter.class)).isSameAs(filter);
                });
    }
}
