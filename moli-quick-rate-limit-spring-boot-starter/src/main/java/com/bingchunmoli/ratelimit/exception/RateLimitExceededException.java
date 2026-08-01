package com.bingchunmoli.ratelimit.exception;

import java.time.Duration;

/**
 * Raised when no permit remains in the current rate limit window.
 */
public class RateLimitExceededException extends RuntimeException {

    private final String key;
    private final long permits;
    private final Duration retryAfter;

    public RateLimitExceededException(String message, String key, long permits, Duration retryAfter) {
        super(message == null || message.isBlank() ? "Too many requests" : message);
        this.key = key;
        this.permits = permits;
        this.retryAfter = retryAfter;
    }

    public String getKey() {
        return key;
    }

    public long getPermits() {
        return permits;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
