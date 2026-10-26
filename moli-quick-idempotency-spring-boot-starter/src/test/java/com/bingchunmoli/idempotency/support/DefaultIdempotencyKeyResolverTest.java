package com.bingchunmoli.idempotency.support;

import com.bingchunmoli.idempotency.annotation.Idempotent;
import com.bingchunmoli.idempotency.autoconfigure.IdempotencyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DefaultIdempotencyKeyResolverTest {
    private final DefaultIdempotencyKeyResolver resolver =
            new DefaultIdempotencyKeyResolver(new ObjectMapper(), new IdempotencyProperties());

    @Test
    void concurrentCallsAlwaysEvaluateTheirOwnTargetAndArguments() throws Exception {
        var method = Work.class.getMethod("key", String.class);
        var annotation = method.getAnnotation(Idempotent.class);
        IntStream.range(0, 100).parallel().forEach(index -> {
            String prefix = index % 2 == 0 ? "first-" : "second-";
            String key = resolver.resolve(new Work(prefix), method, new Object[]{index + ""}, annotation);
            assertThat(key).endsWith(":orders:" + prefix + index);
        });
    }

    @Test
    void sameExpressionCanUseDifferentArgumentTypesAndMethodNamespaces() throws Exception {
        var text = Work.class.getMethod("text", String.class);
        var number = Work.class.getMethod("number", Integer.class);
        for (int i = 0; i < 10; i++) {
            assertThat(resolver.resolve(new Work(""), text, new Object[]{"2"}, text.getAnnotation(Idempotent.class)))
                    .endsWith(".text:21");
            assertThat(resolver.resolve(new Work(""), number, new Object[]{2}, number.getAnnotation(Idempotent.class)))
                    .endsWith(".number:3");
        }
    }

    public static class Work {
        private final String prefix;
        Work(String prefix) { this.prefix = prefix; }
        public String getPrefix() { return prefix; }
        @Idempotent(key = "#root.prefix + #p0", namespace = "orders") public void key(String value) { }
        @Idempotent(key = "#p0 + 1") public void text(String value) { }
        @Idempotent(key = "#p0 + 1") public void number(Integer value) { }
    }
}
