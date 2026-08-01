package com.bingchunmoli.idempotency.core;

import com.bingchunmoli.idempotency.annotation.Idempotent;

import java.lang.reflect.Method;

/**
 * Resolves the final storage key for an intercepted invocation.
 */
@FunctionalInterface
public interface IdempotencyKeyResolver {

    String resolve(Object target, Method method, Object[] arguments, Idempotent idempotent);
}
