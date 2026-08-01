package com.bingchunmoli.idempotency.aspect;

import com.bingchunmoli.idempotency.annotation.Idempotent;
import com.bingchunmoli.idempotency.autoconfigure.IdempotencyProperties;
import com.bingchunmoli.idempotency.core.IdempotencyKeyResolver;
import com.bingchunmoli.idempotency.core.IdempotencyStore;
import com.bingchunmoli.idempotency.core.IdempotencyToken;
import com.bingchunmoli.idempotency.exception.DuplicateRequestException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Applies idempotency around annotated method invocations.
 */
@Aspect
public class IdempotencyAspect implements Ordered {

    private final IdempotencyStore store;
    private final IdempotencyKeyResolver keyResolver;
    private final IdempotencyProperties properties;

    public IdempotencyAspect(IdempotencyStore store, IdempotencyKeyResolver keyResolver,
                             IdempotencyProperties properties) {
        this.store = store;
        this.keyResolver = keyResolver;
        this.properties = properties;
    }

    @Around("@annotation(com.bingchunmoli.idempotency.annotation.Idempotent)")
    public Object invoke(ProceedingJoinPoint joinPoint) throws Throwable {
        Method signatureMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Method method = AopUtils.getMostSpecificMethod(signatureMethod, joinPoint.getTarget().getClass());
        Idempotent idempotent = AnnotatedElementUtils.findMergedAnnotation(method, Idempotent.class);
        if (idempotent == null && method != signatureMethod) {
            idempotent = AnnotatedElementUtils.findMergedAnnotation(signatureMethod, Idempotent.class);
        }
        if (idempotent == null) {
            return joinPoint.proceed();
        }

        String key = keyResolver.resolve(joinPoint.getTarget(), method, joinPoint.getArgs(), idempotent);
        Optional<IdempotencyToken> acquired = store.tryAcquire(key, resolveTimeout(idempotent));
        if (acquired.isEmpty()) {
            throw new DuplicateRequestException(key);
        }

        IdempotencyToken token = acquired.get();
        try {
            Object result = joinPoint.proceed();
            if (idempotent.releaseOnFailure() && result instanceof CompletionStage<?> stage) {
                return stage.whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        store.release(token);
                    }
                });
            }
            return result;
        } catch (Throwable failure) {
            if (idempotent.releaseOnFailure()) {
                store.release(token);
            }
            throw failure;
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
}
