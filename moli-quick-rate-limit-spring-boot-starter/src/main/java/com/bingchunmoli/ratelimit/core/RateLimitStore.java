package com.bingchunmoli.ratelimit.core;

/**
 * Storage strategy for atomically consuming rate limit permits.
 */
@FunctionalInterface
public interface RateLimitStore {

    RateLimitDecision tryAcquire(String key, RateLimitPolicy policy);
}
