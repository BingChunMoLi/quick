package com.bingchunmoli.web.exception;

import org.springframework.http.HttpStatusCode;

import java.time.Instant;
import java.util.Objects;

/**
 * Framework-neutral input for building a Web error response.
 */
public record WebErrorContext(
        HttpStatusCode status,
        String code,
        String message,
        Instant timestamp,
        String path,
        String requestId,
        Object details) {

    public WebErrorContext {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
