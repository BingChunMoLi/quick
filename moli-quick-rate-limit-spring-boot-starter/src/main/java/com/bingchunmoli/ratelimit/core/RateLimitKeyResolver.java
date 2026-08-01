package com.bingchunmoli.ratelimit.core;

import com.bingchunmoli.ratelimit.annotation.RateLimit;

import java.lang.reflect.Method;

/**
 * Resolves the complete storage key for an annotated invocation.
 */
@FunctionalInterface
public interface RateLimitKeyResolver {

    String resolve(Object target, Method method, Object[] arguments, RateLimit rateLimit);
}
