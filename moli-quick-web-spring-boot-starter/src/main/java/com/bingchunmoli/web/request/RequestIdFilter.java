package com.bingchunmoli.web.request;

import com.bingchunmoli.web.autoconfigure.QuickWebProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Propagates a safe request identifier through the response, request attributes and MDC.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_ATTRIBUTE = "moli.web.request-id";

    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]+");

    private final QuickWebProperties.RequestId properties;
    private final RequestIdGenerator generator;

    public RequestIdFilter(QuickWebProperties.RequestId properties, RequestIdGenerator generator) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.generator = Objects.requireNonNull(generator, "generator must not be null");
        validateConfiguration();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        String previousMdcValue = MDC.get(properties.getMdcKey());
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(properties.getHeaderName(), requestId);
        MDC.put(properties.getMdcKey(), requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            restoreMdc(previousMdcValue);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        if (properties.isAcceptIncoming()) {
            String incoming = request.getHeader(properties.getHeaderName());
            if (isValid(incoming)) {
                return incoming;
            }
        }
        String generated = generator.generate();
        if (!isValid(generated)) {
            throw new IllegalStateException("RequestIdGenerator returned an invalid request id");
        }
        return generated;
    }

    private boolean isValid(String value) {
        return StringUtils.hasText(value)
                && value.length() <= properties.getMaxLength()
                && SAFE_REQUEST_ID.matcher(value).matches();
    }

    private void restoreMdc(String previousMdcValue) {
        if (previousMdcValue == null) {
            MDC.remove(properties.getMdcKey());
        } else {
            MDC.put(properties.getMdcKey(), previousMdcValue);
        }
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.getHeaderName())) {
            throw new IllegalArgumentException("moli.web.request-id.header-name must not be blank");
        }
        if (!StringUtils.hasText(properties.getMdcKey())) {
            throw new IllegalArgumentException("moli.web.request-id.mdc-key must not be blank");
        }
        if (properties.getMaxLength() <= 0) {
            throw new IllegalArgumentException("moli.web.request-id.max-length must be positive");
        }
    }
}
