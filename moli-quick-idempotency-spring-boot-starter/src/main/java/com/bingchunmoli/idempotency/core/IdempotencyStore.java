package com.bingchunmoli.idempotency.core;

import java.time.Duration;
import java.util.Optional;

/**
 * Atomic storage contract used by the idempotency aspect.
 */
public interface IdempotencyStore {

    Optional<IdempotencyToken> tryAcquire(String key, Duration timeout);

    /**
     * Releases the key only when the supplied token still owns it.
     */
    void release(IdempotencyToken token);
}
