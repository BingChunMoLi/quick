package com.bingchunmoli.web.autoconfigure;

import com.bingchunmoli.idempotency.annotation.Idempotent;
import com.bingchunmoli.idempotency.autoconfigure.IdempotencyAutoConfiguration;
import com.bingchunmoli.idempotency.exception.DuplicateRequestException;
import com.bingchunmoli.ratelimit.annotation.RateLimit;
import com.bingchunmoli.ratelimit.autoconfigure.RateLimitAutoConfiguration;
import com.bingchunmoli.ratelimit.exception.RateLimitExceededException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class InterfaceProtectionCompositionTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void combinedInterfaceProtectionsRetainRateLimitBeforeIdempotency(boolean cglib) {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                AopAutoConfiguration.class, IdempotencyAutoConfiguration.class, RateLimitAutoConfiguration.class))
                .withPropertyValues("spring.aop.proxy-target-class=" + cglib)
                .withBean("orders", Orders.class, OrderService::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var orders = context.getBean(Orders.class);
                    assertThat(orders.create("one")).isEqualTo(1);
                    assertThatThrownBy(() -> orders.create("one")).isInstanceOf(DuplicateRequestException.class);
                    assertThatThrownBy(() -> orders.create("one")).isInstanceOf(RateLimitExceededException.class);
                    assertThat(orders.create("two")).isEqualTo(2);
                });
    }

    public interface Orders {
        @Idempotent(key = "#p0", timeout = 60)
        @RateLimit(key = "#p0", permits = 2, window = 60)
        int create(String key);
    }
    public static class OrderService implements Orders {
        private final AtomicInteger calls = new AtomicInteger();
        public int create(String key) { return calls.incrementAndGet(); }
    }
}
