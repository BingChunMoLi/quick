package com.bingchunmoli.idempotency.aspect;

import com.bingchunmoli.idempotency.annotation.Idempotent;
import com.bingchunmoli.idempotency.autoconfigure.IdempotencyProperties;
import com.bingchunmoli.idempotency.core.IdempotencyKeyResolver;
import com.bingchunmoli.idempotency.core.IdempotencyStore;
import com.bingchunmoli.idempotency.core.IdempotencyToken;
import com.bingchunmoli.idempotency.exception.DuplicateRequestException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Applies idempotency around annotated method invocations.
 */
public class IdempotencyAspect extends AbstractPointcutAdvisor implements MethodInterceptor {

    private final IdempotencyStore store;
    private final IdempotencyKeyResolver keyResolver;
    private final IdempotencyProperties properties;

    // AbstractPointcutAdvisor compares its Advice; returning this would recurse in equals.
    private final MethodInterceptor advice = this::invoke;

    private final Pointcut pointcut = new StaticMethodMatcherPointcut() {
        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            return annotation(method, targetClass) != null;
        }
    };

    public IdempotencyAspect(IdempotencyStore store, IdempotencyKeyResolver keyResolver,
                             IdempotencyProperties properties) {
        this.store = store;
        this.keyResolver = keyResolver;
        this.properties = properties;
    }

    @Override
    public Pointcut getPointcut() { return pointcut; }

    @Override
    public Advice getAdvice() { return advice; }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        return invoke(invocation.getThis(), invocation.getMethod(), invocation.getArguments(), invocation::proceed);
    }

    /** Retains the direct invocation entry point for existing integrations. */
    public Object invoke(ProceedingJoinPoint joinPoint) throws Throwable {
        return invoke(joinPoint.getTarget(), ((MethodSignature) joinPoint.getSignature()).getMethod(),
                joinPoint.getArgs(), joinPoint::proceed);
    }

    private static Idempotent annotation(Method method, Class<?> targetClass) {
        Method specific = AopUtils.getMostSpecificMethod(method, targetClass);
        Idempotent annotation = AnnotatedElementUtils.findMergedAnnotation(specific, Idempotent.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(method, Idempotent.class);
        }
        if (annotation != null) {
            return annotation;
        }
        // The target may introduce an interface while inheriting its implementation from an unrelated base.
        for (Class<?> contract : ClassUtils.getAllInterfacesForClassAsSet(targetClass)) {
            for (Method candidate : contract.getMethods()) {
                if (candidate.getName().equals(specific.getName())
                        && candidate.getParameterCount() == specific.getParameterCount()
                        && AopUtils.getMostSpecificMethod(candidate, targetClass).equals(specific)) {
                    annotation = AnnotatedElementUtils.findMergedAnnotation(candidate, Idempotent.class);
                    if (annotation != null) {
                        return annotation;
                    }
                }
            }
        }
        return null;
    }

    private Object invoke(Object target, Method signatureMethod, Object[] arguments, Invocation invocation) throws Throwable {
        Method method = AopUtils.getMostSpecificMethod(signatureMethod, target.getClass());
        Idempotent idempotent = annotation(signatureMethod, target.getClass());
        if (idempotent == null) {
            return invocation.proceed();
        }

        String key = keyResolver.resolve(target, method, arguments, idempotent);
        Optional<IdempotencyToken> acquired = store.tryAcquire(key, resolveTimeout(idempotent));
        if (acquired.isEmpty()) {
            throw new DuplicateRequestException(key);
        }

        IdempotencyToken token = acquired.get();
        try {
            Object result = invocation.proceed();
            if (idempotent.releaseOnFailure() && result instanceof CompletionStage<?> stage) {
                // Observe completion without replacing a concrete Future type or breaking cancellation.
                stage.whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        releaseAfterFailure(token, failure);
                    }
                });
            }
            return result;
        } catch (Throwable failure) {
            if (idempotent.releaseOnFailure()) {
                releaseAfterFailure(token, failure);
            }
            throw failure;
        }
    }

    private void releaseAfterFailure(IdempotencyToken token, Throwable failure) {
        try {
            store.release(token);
        } catch (RuntimeException cleanupFailure) {
            if (cleanupFailure != failure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    private Duration resolveTimeout(Idempotent idempotent) {
        Duration timeout = idempotent.timeout() < 0
                ? properties.getDefaultTimeout()
                : Duration.of(idempotent.timeout(), idempotent.timeUnit().toChronoUnit());
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Idempotency timeout must be positive");
        }
        return timeout;
    }

    @Override
    public int getOrder() {
        return properties.getOrder();
    }

    @FunctionalInterface
    private interface Invocation { Object proceed() throws Throwable; }
}
