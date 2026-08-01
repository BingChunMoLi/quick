package com.bingchunmoli.web.request;

import com.bingchunmoli.web.autoconfigure.QuickWebProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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
}
