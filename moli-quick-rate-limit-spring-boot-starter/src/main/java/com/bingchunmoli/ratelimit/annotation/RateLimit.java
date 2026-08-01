package com.bingchunmoli.ratelimit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Limits invocations of a method within a fixed time window.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * SpEL expression used to calculate the limiting dimension. Parameter aliases such as {@code #p0} are always
     * available. When empty, all invocations of the method share one global bucket.
     */
    String key() default "";

    /**
     * Optional logical namespace. By default, the declaring class and method name are used.
     */
    String namespace() default "";

    /**
     * Number of permitted invocations per window. A negative value uses {@code moli.rate-limit.default-permits}.
     */
    long permits() default -1;

    /**
     * Fixed window length. A negative value uses {@code moli.rate-limit.default-window}.
     */
    long window() default -1;

    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * Exception message used when the limit is exceeded.
     */
    String message() default "Too many requests";
}
