package com.bingchunmoli.web.autoconfigure;

import com.bingchunmoli.web.exception.DuplicateRequestExceptionHandler;
import com.bingchunmoli.web.exception.GlobalWebExceptionHandler;
import com.bingchunmoli.web.exception.RateLimitExceptionHandler;
import com.bingchunmoli.web.exception.WebExceptionResponseFactory;
import com.bingchunmoli.web.request.RequestIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WebAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RequestIdAutoConfiguration.class,
                    WebExceptionHandlerAutoConfiguration.class,
                    IdempotencyWebExceptionAutoConfiguration.class,
                    RateLimitWebExceptionAutoConfiguration.class));

    @Test
    void shouldConfigureWebDefaultsAndOptionalExceptionAdapters() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RequestIdGenerator.class);
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
            assertThat(context).hasSingleBean(WebExceptionResponseFactory.class);
            assertThat(context).hasSingleBean(GlobalWebExceptionHandler.class);
            assertThat(context).hasSingleBean(DuplicateRequestExceptionHandler.class);
            assertThat(context).hasSingleBean(RateLimitExceptionHandler.class);
        });
    }

    @Test
    void shouldDisableAllFeaturesGlobally() {
        contextRunner.withPropertyValues("moli.web.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RequestIdGenerator.class);
                    assertThat(context).doesNotHaveBean(WebExceptionResponseFactory.class);
                    assertThat(context).doesNotHaveBean(GlobalWebExceptionHandler.class);
                });
    }

    @Test
    void shouldDisableRequestIdIndependently() {
        contextRunner.withPropertyValues("moli.web.request-id.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RequestIdGenerator.class);
                    assertThat(context).hasSingleBean(GlobalWebExceptionHandler.class);
                });
    }

    @Test
    void shouldBackOffForCustomResponseFactory() {
        WebExceptionResponseFactory custom = mock(WebExceptionResponseFactory.class);
        contextRunner.withBean(WebExceptionResponseFactory.class, () -> custom)
                .run(context -> assertThat(context.getBean(WebExceptionResponseFactory.class)).isSameAs(custom));
    }

    @Test
    void shouldBackOffOptionalAdaptersWhenTheirStartersAreAbsent() {
        contextRunner.withClassLoader(new FilteredClassLoader(
                        "com.bingchunmoli.idempotency",
                        "com.bingchunmoli.ratelimit"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GlobalWebExceptionHandler.class);
                    assertThat(context).doesNotHaveBean(DuplicateRequestExceptionHandler.class);
                    assertThat(context).doesNotHaveBean(RateLimitExceptionHandler.class);
                });
    }
}
