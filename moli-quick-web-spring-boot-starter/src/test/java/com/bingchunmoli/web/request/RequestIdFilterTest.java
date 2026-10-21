package com.bingchunmoli.web.request;

import com.bingchunmoli.web.autoconfigure.QuickWebProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestIdFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldReuseSafeIncomingRequestIdAndClearMdcAfterRequest() throws Exception {
        QuickWebProperties.RequestId properties = new QuickWebProperties.RequestId();
        RequestIdFilter filter = new RequestIdFilter(properties, () -> "generated-id");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "client_123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            assertThat(MDC.get("requestId")).isEqualTo("client_123");
            assertThat(servletRequest.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE))
                    .isEqualTo("client_123");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("client_123");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void shouldReplaceUnsafeIncomingRequestIdAndRestorePreviousMdcValue() throws Exception {
        QuickWebProperties.RequestId properties = new QuickWebProperties.RequestId();
        RequestIdFilter filter = new RequestIdFilter(properties, () -> "generated-id");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "unsafe request id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put("requestId", "outer-request");

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(MDC.get("requestId")).isEqualTo("generated-id"));

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("generated-id");
        assertThat(MDC.get("requestId")).isEqualTo("outer-request");
    }

    @Test
    void shouldRejectInvalidGeneratedRequestId() {
        RequestIdFilter filter = new RequestIdFilter(
                new QuickWebProperties.RequestId(), () -> "invalid generated id");

        assertThatThrownBy(() -> filter.doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(), (request, response) -> {
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RequestIdGenerator returned an invalid request id");
    }

    @Test
    void repeatedAsyncDispatchesReuseTheFirstGeneratedId() throws Exception {
        var properties = new QuickWebProperties.RequestId();
        properties.setAcceptIncoming(false);
        var generated = new AtomicInteger();
        var filter = new RequestIdFilter(properties, () -> "server-" + generated.incrementAndGet());
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "ignored-client-id");
        var response = new MockHttpServletResponse();
        MDC.put("requestId", "outer-context");
        FilterChain chain = (req, res) -> {
            assertThat(MDC.get("requestId")).isEqualTo("server-1");
            assertThat(req.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE)).isEqualTo("server-1");
        };
        filter.doFilter(request, response, chain);
        request.setDispatcherType(DispatcherType.ASYNC);
        filter.doFilter(request, response, chain);
        filter.doFilter(request, response, chain);
        assertThat(generated.get()).isEqualTo(1);
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("server-1");
        assertThat(MDC.get("requestId")).isEqualTo("outer-context");
    }

    @Test
    void asyncChainFailuresRestoreTheDispatchThreadsPreviousMdc() {
        var filter = new RequestIdFilter(new QuickWebProperties.RequestId(), () -> {
            throw new AssertionError("the existing request id must be reused");
        });
        var request = new MockHttpServletRequest();
        request.setDispatcherType(DispatcherType.ASYNC);
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "original-id");
        MDC.put("requestId", "async-outer");
        var failure = new ServletException("downstream failure");
        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertThat(MDC.get("requestId")).isEqualTo("original-id");
            throw failure;
        })).isSameAs(failure);
        assertThat(MDC.get("requestId")).isEqualTo("async-outer");
    }
}
