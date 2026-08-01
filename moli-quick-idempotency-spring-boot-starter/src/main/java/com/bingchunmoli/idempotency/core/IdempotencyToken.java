package com.bingchunmoli.idempotency.core;

import java.util.Objects;

/**
 * Ownership token returned by an idempotency store.
 */
public record IdempotencyToken(String key, String value) {

    public IdempotencyToken {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        value = Objects.requireNonNull(value, "value must not be null");
    }
}
