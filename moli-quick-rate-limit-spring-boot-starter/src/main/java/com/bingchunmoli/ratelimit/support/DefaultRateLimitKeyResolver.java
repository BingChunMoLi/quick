package com.bingchunmoli.ratelimit.support;

import com.bingchunmoli.ratelimit.annotation.RateLimit;
import com.bingchunmoli.ratelimit.autoconfigure.RateLimitProperties;
import com.bingchunmoli.ratelimit.core.RateLimitKeyResolver;
import com.bingchunmoli.ratelimit.exception.RateLimitKeyException;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.StringUtils;
import org.springframework.util.ConcurrentLruCache;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Arrays;

/**
 * Resolves SpEL dimensions and method-global default keys.
 */
public class DefaultRateLimitKeyResolver implements RateLimitKeyResolver {

    private static final String GLOBAL_DIMENSION = "global";

    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ConcurrentLruCache<String, Expression> expressions =
            new ConcurrentLruCache<>(256, expressionParser::parseExpression);
    private final ConcurrentLruCache<Method, String> namespaces =
            new ConcurrentLruCache<>(256, this::defaultNamespace);
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final RateLimitProperties properties;

    public DefaultRateLimitKeyResolver(RateLimitProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public String resolve(Object target, Method method, Object[] arguments, RateLimit rateLimit) {
        String dimension = StringUtils.hasText(rateLimit.key())
                ? evaluateExpression(target, method, arguments, rateLimit.key())
                : GLOBAL_DIMENSION;
        String namespace = StringUtils.hasText(rateLimit.namespace())
                ? rateLimit.namespace().trim()
                : namespaces.get(method);
        String prefix = requireText(properties.getKeyPrefix(), "moli.rate-limit.key-prefix");
        return prefix + ":" + namespace + ":" + requireText(dimension, "resolved rate limit key");
    }

    private String defaultNamespace(Method method) {
        String namespace = method.getDeclaringClass().getName() + "." + method.getName();
        boolean overloaded = Arrays.stream(method.getDeclaringClass().getDeclaredMethods())
                .anyMatch(candidate -> !candidate.isBridge() && !candidate.isSynthetic()
                        && candidate.getName().equals(method.getName())
                        && !Arrays.equals(candidate.getParameterTypes(), method.getParameterTypes()));
        // Preserve existing keys for ordinary methods; compiler bridges are not business overloads.
        return overloaded ? namespace + "(" + String.join(",", Arrays.stream(method.getParameterTypes())
                .map(Class::getTypeName).toList()) + ")" : namespace;
    }

    private String evaluateExpression(Object target, Method method, Object[] arguments, String expression) {
        try {
            MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                    target, method, arguments == null ? new Object[0] : arguments, parameterNameDiscoverer);
            Object value = expressions.get(expression).getValue(context);
            return value == null ? null : String.valueOf(value);
        } catch (RuntimeException ex) {
            throw new RateLimitKeyException("Failed to evaluate rate limit SpEL: " + expression, ex);
        }
    }

    private String requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new RateLimitKeyException(name + " must not be blank");
        }
        return value.trim();
    }
}
