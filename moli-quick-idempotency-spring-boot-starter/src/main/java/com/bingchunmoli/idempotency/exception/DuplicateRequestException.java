package com.bingchunmoli.idempotency.exception;

/**
 * Raised when an idempotency key is already held.
 */
public class DuplicateRequestException extends RuntimeException {

    private final String idempotencyKey;

    public DuplicateRequestException(String idempotencyKey) {
        super("Duplicate request rejected for idempotency key: " + idempotencyKey);
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
