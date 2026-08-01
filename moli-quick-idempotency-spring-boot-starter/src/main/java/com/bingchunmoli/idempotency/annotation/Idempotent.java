package com.bingchunmoli.idempotency.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Prevents repeated execution of a method for the same resolved key.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * SpEL expression used to calculate the business key. Parameter aliases such as {@code #p0} are always available.
     * When empty, a SHA-256 digest of the method arguments is used.
     */
    String key() default "";

    /**
     * Optional logical namespace. By default, the declaring class and method name are used.
     */
    String namespace() default "";

    /**
     * Key lifetime. A negative value uses {@code moli.idempotency.default-timeout}.
     */
    long timeout() default -1;

    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * Whether the acquired key should be removed if the invocation fails.
     */
    boolean releaseOnFailure() default true;
}
