package com.bingchunmoli.ratelimit.aspect;

import com.bingchunmoli.ratelimit.annotation.RateLimit;
import com.bingchunmoli.ratelimit.autoconfigure.RateLimitProperties;
import com.bingchunmoli.ratelimit.core.RateLimitDecision;
import com.bingchunmoli.ratelimit.core.RateLimitKeyResolver;
import com.bingchunmoli.ratelimit.core.RateLimitPolicy;
import com.bingchunmoli.ratelimit.core.RateLimitStore;
import com.bingchunmoli.ratelimit.exception.RateLimitExceededException;
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

/**
 * Applies fixed-window rate limiting around annotated method invocations.
 */
public class RateLimitAspect extends AbstractPointcutAdvisor implements MethodInterceptor {

    private final RateLimitStore store;
    private final RateLimitKeyResolver keyResolver;
    private final RateLimitProperties properties;

    // AbstractPointcutAdvisor compares its Advice; returning this would recurse in equals.
    private final MethodInterceptor advice = this::invoke;

    private final Pointcut pointcut = new StaticMethodMatcherPointcut() {
        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            return annotation(method, targetClass) != null;
        }
    };

    public RateLimitAspect(RateLimitStore store, RateLimitKeyResolver keyResolver, RateLimitProperties properties) {
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

    private static RateLimit annotation(Method method, Class<?> targetClass) {
        Method specific = AopUtils.getMostSpecificMethod(method, targetClass);
        RateLimit annotation = AnnotatedElementUtils.findMergedAnnotation(specific, RateLimit.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(method, RateLimit.class);
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
                    annotation = AnnotatedElementUtils.findMergedAnnotation(candidate, RateLimit.class);
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
        RateLimit rateLimit = annotation(signatureMethod, target.getClass());
        if (rateLimit == null) {
            return invocation.proceed();
        }

        RateLimitPolicy policy = resolvePolicy(rateLimit);
        String key = keyResolver.resolve(target, method, arguments, rateLimit);
        RateLimitDecision decision = store.tryAcquire(key, policy);
        if (!decision.allowed()) {
            throw new RateLimitExceededException(
                    rateLimit.message(), key, policy.permits(), decision.resetAfter());
        }
        return invocation.proceed();
    }

    private RateLimitPolicy resolvePolicy(RateLimit rateLimit) {
        long permits = rateLimit.permits() < 0 ? properties.getDefaultPermits() : rateLimit.permits();
        Duration window = rateLimit.window() < 0
                ? properties.getDefaultWindow()
                : Duration.of(rateLimit.window(), rateLimit.timeUnit().toChronoUnit());
        return new RateLimitPolicy(permits, window);
    }

    @Override
    public int getOrder() {
        return properties.getOrder();
    }

    @FunctionalInterface
    private interface Invocation { Object proceed() throws Throwable; }
}
