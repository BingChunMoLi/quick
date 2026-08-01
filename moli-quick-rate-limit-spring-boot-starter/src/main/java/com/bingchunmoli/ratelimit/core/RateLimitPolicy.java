package com.bingchunmoli.ratelimit.core;

import java.time.Duration;

/**
 * A fixed-window rate limit policy.
 *
 * @param permits maximum permitted invocations in one window
 * @param window fixed window duration
 */
public record RateLimitPolicy(long permits, Duration window) {

    public RateLimitPolicy {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be positive");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
    }
}
