package com.bingchunmoli.web.exception;

import com.bingchunmoli.web.request.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

/**
 * Shared response construction for Web exception handlers.
 */
public abstract class AbstractWebExceptionHandler {

    private final WebExceptionResponseFactory responseFactory;

    protected AbstractWebExceptionHandler(WebExceptionResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    protected ResponseEntity<?> response(HttpStatusCode status, String code, String message,
                                         HttpServletRequest request, Object details) {
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        WebErrorContext context = new WebErrorContext(
                status,
                code,
                message,
                Instant.now(),
                request.getRequestURI(),
                requestId == null ? null : requestId.toString(),
                details);
        return responseFactory.create(context);
    }
}
