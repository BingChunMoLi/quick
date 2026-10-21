package com.bingchunmoli.web.request;

import com.bingchunmoli.web.autoconfigure.QuickWebProperties;
import com.bingchunmoli.web.autoconfigure.RequestIdAutoConfiguration;
import com.bingchunmoli.web.exception.ApiException;
import com.bingchunmoli.web.exception.DefaultWebExceptionResponseFactory;
import com.bingchunmoli.web.exception.GlobalWebExceptionHandler;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class RequestIdAsyncDispatchTest {
    private Map<String, String> previousMdc;

    @BeforeEach
    void saveMdc() {
        previousMdc = MDC.getCopyOfContextMap();
        MDC.clear();
    }

    @AfterEach
    void restoreMdc() {
        MDC.clear();
        if (previousMdc != null) {
            MDC.setContextMap(previousMdc);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void callableResponsesKeepTheIdAcrossDispatchThreads(boolean clientId) throws Exception {
        var fixture = fixture(new QuickWebProperties());
        String id = clientId ? "client-123" : "server-1";
        var initial = start(fixture, "/callable", clientId ? id : null);
        assertThat(initial.getResponse().getHeader("X-Request-Id")).isEqualTo(id);
        var result = finish(fixture, initial, clientId ? "async-outer" : null);
        status().isOk().match(result);
        jsonPath("$.ok").value(true).match(result);
        assertDispatch(fixture, result, id);
        assertThat(fixture.generated().get()).isEqualTo(clientId ? 0 : 1);
    }

    @Test
    void disabledIncomingIdsAreReplacedOnlyOnce() throws Exception {
        var properties = new QuickWebProperties();
        properties.getRequestId().setAcceptIncoming(false);
        var fixture = fixture(properties);
        var initial = start(fixture, "/callable", "ignored-client");
        assertThat(initial.getResponse().getHeader("X-Request-Id")).isEqualTo("server-1");
        assertDispatch(fixture, finish(fixture, initial, "async-outer"), "server-1");
        assertThat(fixture.generated().get()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"unsafe id", "line\r\nbreak", "123456789012345"})
    void rejectedIncomingIdsAreReplacedOnlyOnce(String incoming) throws Exception {
        var properties = new QuickWebProperties();
        properties.getRequestId().setMaxLength(12);
        var fixture = fixture(properties);
        var initial = start(fixture, "/callable", incoming);
        assertThat(initial.getResponse().getHeader("X-Request-Id")).isEqualTo("server-1");
        assertDispatch(fixture, finish(fixture, initial, null), "server-1");
        assertThat(fixture.generated().get()).isEqualTo(1);
    }

    @Test
    void customHeaderAndMdcKeyDoNotChangeOtherContextEntries() throws Exception {
        var properties = new QuickWebProperties();
        properties.getRequestId().setHeaderName("X-Correlation-Id");
        properties.getRequestId().setMdcKey("traceId");
        var fixture = fixture(properties);
        MDC.put("requestId", "unrelated-main-id");
        var initial = start(fixture, "/callable", "custom-123");
        var result = finish(fixture, initial, "custom-outer");
        assertDispatch(fixture, result, "custom-123");
        assertThat(fixture.inspector().last.get().mdc().get("requestId")).isEqualTo("unrelated-async-id");
        assertThat(MDC.get("requestId")).isEqualTo("unrelated-main-id");
        assertThat(result.getResponse().getHeader("X-Request-Id")).isNull();
        assertThat(fixture.generated().get()).isZero();
    }

    @Test
    void deferredResultsAlsoBindTheIdOnFinalDispatch() throws Exception {
        var fixture = fixture(new QuickWebProperties());
        var result = finish(fixture, start(fixture, "/deferred", null), "async-outer");
        status().isOk().match(result);
        jsonPath("$.ok").value(true).match(result);
        assertDispatch(fixture, result, "server-1");
        assertThat(fixture.generated().get()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"/callable-error", "/deferred-error"})
    void asyncErrorResponsesKeepTheIdAndRestoreThreadContext(String path) throws Exception {
        var fixture = fixture(new QuickWebProperties());
        var result = finish(fixture, start(fixture, path, null), "async-outer");
        status().isConflict().match(result);
        jsonPath("$.code").value("ORDER_CONFLICT").match(result);
        jsonPath("$.data.requestId").value("server-1").match(result);
        assertDispatch(fixture, result, "server-1");
        assertThat(fixture.generated().get()).isEqualTo(1);
    }

    private static Fixture fixture(QuickWebProperties properties) {
        var generated = new AtomicInteger();
        var inspector = new DispatchInspector();
        var registration = new RequestIdAutoConfiguration().moliRequestIdFilterRegistration(
                properties, () -> "server-" + generated.incrementAndGet());
        var mvc = standaloneSetup(new Endpoint()).setControllerAdvice(inspector,
                        new GlobalWebExceptionHandler(new DefaultWebExceptionResponseFactory(), properties))
                .setAsyncRequestTimeout(5000).addFilters(registration.getFilter()).build();
        return new Fixture(mvc, properties.getRequestId(), generated, inspector);
    }

    private static MvcResult start(Fixture fixture, String path, String incoming) throws Exception {
        MDC.put(fixture.properties().getMdcKey(), "request-outer");
        MDC.put("untouched", "main-context");
        var request = get(path);
        if (incoming != null) {
            request.header(fixture.properties().getHeaderName(), incoming);
        }
        var initial = fixture.mvc().perform(request).andExpect(request().asyncStarted()).andReturn();
        assertThat(MDC.get(fixture.properties().getMdcKey())).isEqualTo("request-outer");
        assertThat(MDC.get("untouched")).isEqualTo("main-context");
        return initial;
    }

    private static MvcResult finish(Fixture fixture, MvcResult initial, String outerMdc) throws Exception {
        initial.getAsyncResult(5000);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var result = pool.submit(() -> {
                String key = fixture.properties().getMdcKey();
                if (outerMdc != null) {
                    MDC.put(key, outerMdc);
                }
                MDC.put("untouched", "async-context");
                if (!key.equals("requestId")) {
                    MDC.put("requestId", "unrelated-async-id");
                }
                try {
                    var response = fixture.mvc().perform(asyncDispatch(initial)).andReturn();
                    assertThat(MDC.get(key)).isEqualTo(outerMdc);
                    assertThat(MDC.get("untouched")).isEqualTo("async-context");
                    if (!key.equals("requestId")) {
                        assertThat(MDC.get("requestId")).isEqualTo("unrelated-async-id");
                    }
                    return response;
                } finally {
                    MDC.clear();
                }
            }).get(5, TimeUnit.SECONDS);
            assertThat(MDC.get(fixture.properties().getMdcKey())).isEqualTo("request-outer");
            assertThat(MDC.get("untouched")).isEqualTo("main-context");
            return result;
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void assertDispatch(Fixture fixture, MvcResult result, String id) {
        var snapshot = fixture.inspector().last.get();
        assertThat(snapshot.dispatch()).isEqualTo(DispatcherType.ASYNC);
        assertThat(snapshot.requestId()).isEqualTo(id);
        assertThat(snapshot.mdc().get(fixture.properties().getMdcKey())).isEqualTo(id);
        assertThat(snapshot.mdc().get("untouched")).isEqualTo("async-context");
        assertThat(result.getResponse().getHeader(fixture.properties().getHeaderName())).isEqualTo(id);
    }

    private record Fixture(MockMvc mvc, QuickWebProperties.RequestId properties,
                           AtomicInteger generated, DispatchInspector inspector) { }
    private record Snapshot(DispatcherType dispatch, Object requestId, Map<String, String> mdc) { }

    @RestControllerAdvice
    static class DispatchInspector implements ResponseBodyAdvice<Object> {
        final AtomicReference<Snapshot> last = new AtomicReference<>();
        @Override
        public boolean supports(MethodParameter type, Class<? extends HttpMessageConverter<?>> converter) { return true; }
        @Override
        public Object beforeBodyWrite(Object body, MethodParameter type, MediaType mediaType,
                                      Class<? extends HttpMessageConverter<?>> converter,
                                      ServerHttpRequest request, ServerHttpResponse response) {
            var servlet = ((ServletServerHttpRequest) request).getServletRequest();
            last.set(new Snapshot(servlet.getDispatcherType(), servlet.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE),
                    MDC.getCopyOfContextMap()));
            return body;
        }
    }

    @RestController
    static class Endpoint {
        @GetMapping("/callable") Callable<Map<String, Boolean>> callable() { return () -> Map.of("ok", true); }
        @GetMapping("/callable-error") Callable<Map<String, Boolean>> callableError() {
            return () -> { throw new ApiException(HttpStatus.CONFLICT, "ORDER_CONFLICT", "conflict"); };
        }
        @GetMapping("/deferred") DeferredResult<Map<String, Boolean>> deferred() {
            var result = new DeferredResult<Map<String, Boolean>>();
            result.setResult(Map.of("ok", true));
            return result;
        }
        @GetMapping("/deferred-error") DeferredResult<Map<String, Boolean>> deferredError() {
            var result = new DeferredResult<Map<String, Boolean>>();
            result.setErrorResult(new ApiException(HttpStatus.CONFLICT, "ORDER_CONFLICT", "conflict"));
            return result;
        }
    }
}
