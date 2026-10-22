package com.bingchunmoli.web.autoconfigure;

import com.bingchunmoli.web.exception.DuplicateRequestExceptionHandler;
import com.bingchunmoli.web.exception.GlobalWebExceptionHandler;
import com.bingchunmoli.web.exception.RateLimitExceptionHandler;
import com.bingchunmoli.web.exception.WebExceptionResponseFactory;
import com.bingchunmoli.web.request.RequestIdGenerator;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebAutoConfigurationTest {

    @Test
    void requestIdRegistrationSupportsRequestAndAsyncDispatches() throws Exception {
        var registration = new RequestIdAutoConfiguration()
                .moliRequestIdFilterRegistration(new QuickWebProperties(), () -> "generated-id");
        var servletContext = mock(ServletContext.class);
        var dynamic = mock(FilterRegistration.Dynamic.class);
        when(servletContext.addFilter("moliRequestIdFilter", registration.getFilter())).thenReturn(dynamic);
        registration.onStartup(servletContext);
        verify(dynamic).setAsyncSupported(true);
        verify(dynamic).addMappingForUrlPatterns(
                EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC), false, "/*");
    }

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
    void shortMaximumWithTheDefaultGeneratorFailsAtStartup() {
        contextRunner.withPropertyValues("moli.web.request-id.max-length=16").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("at least 32");
        });
    }

    @Test
    void shortMaximumWorksWithACustomGeneratorAndDoesNotValidateWhenDisabled() {
        contextRunner.withPropertyValues("moli.web.request-id.max-length=16")
                .withBean(RequestIdGenerator.class, () -> () -> "short-id").run(context -> {
                    assertThat(context).hasNotFailed();
                    var registration = context.getBean("moliRequestIdFilterRegistration", FilterRegistrationBean.class);
                    var response = new MockHttpServletResponse();
                    registration.getFilter().doFilter(new MockHttpServletRequest("GET", "/"), response, (req, res) -> { });
                    assertThat(response.getHeader("X-Request-Id")).isEqualTo("short-id");
                });
        contextRunner.withPropertyValues("moli.web.request-id.max-length=16", "moli.web.enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
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
