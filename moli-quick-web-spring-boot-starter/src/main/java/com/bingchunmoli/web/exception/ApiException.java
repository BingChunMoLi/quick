package com.bingchunmoli.web.exception;

import org.springframework.http.HttpStatusCode;

import java.util.Objects;

/**
 * Explicit business exception carrying an HTTP status, business code and optional response details.
 */
public class ApiException extends RuntimeException {

    private final HttpStatusCode status;
    private final String code;
    private final Object details;

    public ApiException(HttpStatusCode status, String code, String message) {
        this(status, code, message, null, null);
    }

    public ApiException(HttpStatusCode status, String code, String message, Object details) {
        this(status, code, message, details, null);
    }

    public ApiException(HttpStatusCode status, String code, String message, Object details, Throwable cause) {
        super(message, cause);
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        this.code = code;
        this.details = details;
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Object getDetails() {
        return details;
    }
}
