package com.bingchunmoli.web.exception;

import com.bingchunmoli.ratelimit.exception.RateLimitExceededException;
import com.bingchunmoli.web.autoconfigure.QuickWebProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Duration;
import java.util.Map;

/**
 * Maps rate limit failures to HTTP 429 and a standards-compatible Retry-After header.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class RateLimitExceptionHandler extends AbstractWebExceptionHandler {

    private final QuickWebProperties.ExceptionHandling properties;

    public RateLimitExceptionHandler(WebExceptionResponseFactory responseFactory, QuickWebProperties properties) {
        super(responseFactory);
        this.properties = properties.getExceptionHandling();
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<?> handle(RateLimitExceededException exception, HttpServletRequest request) {
        Duration retryAfter = exception.getRetryAfter() == null ? Duration.ZERO : exception.getRetryAfter();
        long retryAfterMillis = Math.max(0, retryAfter.toMillis());
        long retryAfterSeconds = Math.max(
                1, retryAfterMillis / 1000 + (retryAfterMillis % 1000 == 0 ? 0 : 1));
        ResponseEntity<?> response = response(
                HttpStatus.TOO_MANY_REQUESTS,
                properties.getRateLimitCode(),
                exception.getMessage(),
                request,
                Map.of("permits", exception.getPermits(), "retryAfterSeconds", retryAfterSeconds));
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
                .body(response.getBody());
    }
}
