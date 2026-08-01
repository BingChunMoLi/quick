package com.bingchunmoli.idempotency.support;

import com.bingchunmoli.idempotency.annotation.Idempotent;
import com.bingchunmoli.idempotency.autoconfigure.IdempotencyProperties;
import com.bingchunmoli.idempotency.core.IdempotencyKeyResolver;
import com.bingchunmoli.idempotency.exception.IdempotencyKeyException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Resolves SpEL keys and generates deterministic argument digests when no expression is supplied.
 */
public class DefaultIdempotencyKeyResolver implements IdempotencyKeyResolver {

    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final ObjectMapper objectMapper;
    private final IdempotencyProperties properties;

    public DefaultIdempotencyKeyResolver(ObjectMapper objectMapper, IdempotencyProperties properties) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public String resolve(Object target, Method method, Object[] arguments, Idempotent idempotent) {
        String businessKey = StringUtils.hasText(idempotent.key())
                ? evaluateExpression(target, method, arguments, idempotent.key())
                : digestArguments(method, arguments);
        String namespace = StringUtils.hasText(idempotent.namespace())
                ? idempotent.namespace().trim()
                : method.getDeclaringClass().getName() + "." + method.getName();
        String prefix = requireText(properties.getKeyPrefix(), "moli.idempotency.key-prefix");
        return prefix + ":" + namespace + ":" + requireText(businessKey, "resolved idempotency key");
    }

    private String evaluateExpression(Object target, Method method, Object[] arguments, String expression) {
        try {
            MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                    target, method, arguments == null ? new Object[0] : arguments, parameterNameDiscoverer);
            Object value = expressionParser.parseExpression(expression).getValue(context);
            return value == null ? null : String.valueOf(value);
        } catch (RuntimeException ex) {
            throw new IdempotencyKeyException("Failed to evaluate idempotency SpEL: " + expression, ex);
        }
    }

    private String digestArguments(Method method, Object[] arguments) {
        try {
            byte[] methodBytes = method.toGenericString().getBytes(StandardCharsets.UTF_8);
            byte[] argumentBytes = objectMapper.writeValueAsBytes(arguments == null ? new Object[0] : arguments);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(methodBytes);
            return HexFormat.of().formatHex(digest.digest(argumentBytes));
        } catch (JsonProcessingException ex) {
            throw new IdempotencyKeyException(
                    "Method arguments cannot be serialized; configure an explicit @Idempotent key", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IdempotencyKeyException(name + " must not be blank");
        }
        return value.trim();
    }
}
