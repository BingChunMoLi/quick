package com.bingchunmoli.ratelimit.exception;

/**
 * Raised when a rate limit key cannot be resolved.
 */
public class RateLimitKeyException extends RuntimeException {

    public RateLimitKeyException(String message) {
        super(message);
    }

    public RateLimitKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
