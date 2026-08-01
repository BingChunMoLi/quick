package com.bingchunmoli.ratelimit.core;

import java.time.Duration;

/**
 * Result of consuming one permit from a rate limit bucket.
 *
 * @param allowed whether the invocation is allowed
 * @param remainingPermits remaining permits in the current window
 * @param resetAfter duration until the current window resets
 */
public record RateLimitDecision(boolean allowed, long remainingPermits, Duration resetAfter) {

    public RateLimitDecision {
        if (remainingPermits < 0) {
            throw new IllegalArgumentException("remainingPermits must not be negative");
        }
        if (resetAfter == null || resetAfter.isNegative()) {
            throw new IllegalArgumentException("resetAfter must not be negative");
        }
    }
}
