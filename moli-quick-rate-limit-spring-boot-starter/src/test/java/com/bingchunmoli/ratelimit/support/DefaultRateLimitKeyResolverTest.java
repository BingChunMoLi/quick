package com.bingchunmoli.ratelimit.support;

import com.bingchunmoli.ratelimit.annotation.RateLimit;
import com.bingchunmoli.ratelimit.autoconfigure.RateLimitProperties;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DefaultRateLimitKeyResolverTest {
    private final DefaultRateLimitKeyResolver resolver = new DefaultRateLimitKeyResolver(new RateLimitProperties());

    @Test
    void concurrentCallsAlwaysEvaluateTheirOwnTargetAndArguments() throws Exception {
        var method = Work.class.getMethod("key", String.class);
        var annotation = method.getAnnotation(RateLimit.class);
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
            assertThat(resolver.resolve(new Work(""), text, new Object[]{"2"}, text.getAnnotation(RateLimit.class)))
                    .endsWith(".text:21");
            assertThat(resolver.resolve(new Work(""), number, new Object[]{2}, number.getAnnotation(RateLimit.class)))
                    .endsWith(".number:3");
        }
    }

    public static class Work {
        private final String prefix;
        Work(String prefix) { this.prefix = prefix; }
        public String getPrefix() { return prefix; }
        @RateLimit(key = "#root.prefix + #p0", namespace = "orders") public void key(String value) { }
        @RateLimit(key = "#p0 + 1") public void text(String value) { }
        @RateLimit(key = "#p0 + 1") public void number(Integer value) { }
    }
}
