package com.bingchunmoli.ratelimit.aspect;

import com.bingchunmoli.ratelimit.annotation.RateLimit;
import com.bingchunmoli.ratelimit.autoconfigure.RateLimitProperties;
import com.bingchunmoli.ratelimit.core.RateLimitDecision;
import com.bingchunmoli.ratelimit.core.RateLimitKeyResolver;
import com.bingchunmoli.ratelimit.core.RateLimitPolicy;
import com.bingchunmoli.ratelimit.core.RateLimitStore;
import com.bingchunmoli.ratelimit.exception.RateLimitExceededException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * Applies fixed-window rate limiting around annotated method invocations.
 */
@Aspect
public class RateLimitAspect implements Ordered {

    private final RateLimitStore store;
    private final RateLimitKeyResolver keyResolver;
    private final RateLimitProperties properties;

    public RateLimitAspect(RateLimitStore store, RateLimitKeyResolver keyResolver, RateLimitProperties properties) {
        this.store = store;
        this.keyResolver = keyResolver;
        this.properties = properties;
    }

    @Around("@annotation(com.bingchunmoli.ratelimit.annotation.RateLimit)")
    public Object invoke(ProceedingJoinPoint joinPoint) throws Throwable {
        Method signatureMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Method method = AopUtils.getMostSpecificMethod(signatureMethod, joinPoint.getTarget().getClass());
        RateLimit rateLimit = AnnotatedElementUtils.findMergedAnnotation(method, RateLimit.class);
        if (rateLimit == null && method != signatureMethod) {
            rateLimit = AnnotatedElementUtils.findMergedAnnotation(signatureMethod, RateLimit.class);
        }
        if (rateLimit == null) {
            return joinPoint.proceed();
        }

        RateLimitPolicy policy = resolvePolicy(rateLimit);
        String key = keyResolver.resolve(joinPoint.getTarget(), method, joinPoint.getArgs(), rateLimit);
        RateLimitDecision decision = store.tryAcquire(key, policy);
        if (!decision.allowed()) {
            throw new RateLimitExceededException(
                    rateLimit.message(), key, policy.permits(), decision.resetAfter());
        }
        return joinPoint.proceed();
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
}
