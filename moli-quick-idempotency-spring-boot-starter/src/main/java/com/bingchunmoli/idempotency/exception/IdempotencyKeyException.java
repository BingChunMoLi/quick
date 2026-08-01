package com.bingchunmoli.idempotency.exception;

/**
 * Raised when an idempotency key cannot be calculated.
 */
public class IdempotencyKeyException extends RuntimeException {

    public IdempotencyKeyException(String message) {
        super(message);
    }

    public IdempotencyKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
