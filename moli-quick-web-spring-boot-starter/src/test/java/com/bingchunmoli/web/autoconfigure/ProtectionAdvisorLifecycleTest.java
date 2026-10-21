package com.bingchunmoli.web.autoconfigure;

import com.bingchunmoli.idempotency.annotation.Idempotent;
import com.bingchunmoli.idempotency.aspect.IdempotencyAspect;
import com.bingchunmoli.idempotency.autoconfigure.IdempotencyAutoConfiguration;
import com.bingchunmoli.idempotency.autoconfigure.IdempotencyProperties;
import com.bingchunmoli.idempotency.exception.DuplicateRequestException;
import com.bingchunmoli.idempotency.store.InMemoryIdempotencyStore;
import com.bingchunmoli.idempotency.support.DefaultIdempotencyKeyResolver;
import com.bingchunmoli.ratelimit.annotation.RateLimit;
import com.bingchunmoli.ratelimit.aspect.RateLimitAspect;
import com.bingchunmoli.ratelimit.autoconfigure.RateLimitAutoConfiguration;
import com.bingchunmoli.ratelimit.autoconfigure.RateLimitProperties;
import com.bingchunmoli.ratelimit.exception.RateLimitExceededException;
import com.bingchunmoli.ratelimit.store.InMemoryRateLimitStore;
import com.bingchunmoli.ratelimit.support.DefaultRateLimitKeyResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class ProtectionAdvisorLifecycleTest {
    @Test
    void differentAdvisorInstancesHaveSafeSymmetricEqualityAndWorkInCollections() {
        List<Advisor> advisors = List.of(idempotency(), idempotency(), rateLimit(), rateLimit());
        for (Advisor left : advisors) {
            assertThat(left.getAdvice()).isSameAs(left.getAdvice()).isNotSameAs(left);
            assertThat(left.equals(null)).isFalse();
            for (Advisor right : advisors) {
                assertThat(left.equals(right)).isEqualTo(left == right);
                assertThat(right.equals(left)).isEqualTo(left == right);
            }
        }
        assertThat(new HashSet<>(advisors)).hasSize(4);
        for (Advisor advisor : advisors) {
            assertThat(advisors.indexOf(advisor)).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void equivalentSpringWrappersRetainTheParentEqualityAndHashCodeContract() {
        for (var advisor : List.of(idempotency(), rateLimit())) {
            var wrapper = new DefaultPointcutAdvisor(advisor.getPointcut(), advisor.getAdvice());
            assertThat(advisor.equals(wrapper)).isTrue();
            assertThat(wrapper.equals(advisor)).isTrue();
            assertThat(advisor.hashCode()).isEqualTo(wrapper.hashCode());
            var unrelated = new DefaultPointcutAdvisor();
            assertThat(advisor.equals(unrelated)).isFalse();
            assertThat(unrelated.equals(advisor)).isFalse();
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void removingTheLaterAdvisorLeavesRateLimitingOperational(boolean cglib) {
        runner(cglib).run(context -> {
            assertThat(context).hasNotFailed();
            var orders = context.getBean(Orders.class);
            var proxy = (Advised) orders;
            var idem = context.getBean(IdempotencyAspect.class);
            var rate = context.getBean(RateLimitAspect.class);
            assertThat(proxy.getAdvisors()).containsExactly(rate, idem);
            assertThat(orders.create("one")).isEqualTo(1);
            assertThat(proxy.removeAdvisor(idem)).isTrue();
            assertThat(proxy.getAdvisors()).containsExactly(rate);
            assertThat(orders.create("one")).isEqualTo(2);
            assertThatThrownBy(() -> orders.create("one")).isInstanceOf(RateLimitExceededException.class);
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void removingAdviceAndReaddingAdvisorsRefreshesTheInvocationChain(boolean cglib) {
        runner(cglib).run(context -> {
            var orders = context.getBean(Orders.class);
            var proxy = (Advised) orders;
            var idem = context.getBean(IdempotencyAspect.class);
            var rate = context.getBean(RateLimitAspect.class);
            assertThat(orders.create("one")).isEqualTo(1);
            assertThat(proxy.indexOf(idem.getAdvice())).isEqualTo(1);
            assertThat(proxy.removeAdvice(idem.getAdvice())).isTrue();
            assertThat(proxy.indexOf(idem.getAdvice())).isEqualTo(-1);
            assertThat(orders.create("one")).isEqualTo(2);
            assertThat(proxy.removeAdvisor(rate)).isTrue();
            proxy.addAdvisor(idem);
            assertThatThrownBy(() -> orders.create("one")).isInstanceOf(DuplicateRequestException.class);
            assertThat(orders.create("two")).isEqualTo(3);
            proxy.addAdvisor(0, rate);
            assertThat(orders.create("three")).isEqualTo(4);
            assertThatThrownBy(() -> orders.create("three")).isInstanceOf(DuplicateRequestException.class);
            assertThatThrownBy(() -> orders.create("three")).isInstanceOf(RateLimitExceededException.class);
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void removingUninstalledInstancesDoesNotRemoveActiveProtection(boolean cglib) {
        runner(cglib).run(context -> {
            var orders = context.getBean(Orders.class);
            var proxy = (Advised) orders;
            assertThat(proxy.removeAdvisor(idempotency())).isFalse();
            assertThat(proxy.removeAdvisor(rateLimit())).isFalse();
            assertThat(proxy.removeAdvice(idempotency().getAdvice())).isFalse();
            assertThat(proxy.getAdvisors()).hasSize(2);
            orders.create("one");
            assertThatThrownBy(() -> orders.create("one")).isInstanceOf(DuplicateRequestException.class);
            assertThatThrownBy(() -> orders.create("one")).isInstanceOf(RateLimitExceededException.class);
        });
    }

    private static ApplicationContextRunner runner(boolean cglib) {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                AopAutoConfiguration.class, IdempotencyAutoConfiguration.class, RateLimitAutoConfiguration.class))
                .withPropertyValues("spring.aop.proxy-target-class=" + cglib)
                .withBean("orders", Orders.class, OrderService::new);
    }

    private static IdempotencyAspect idempotency() {
        var properties = new IdempotencyProperties();
        return new IdempotencyAspect(new InMemoryIdempotencyStore(),
                new DefaultIdempotencyKeyResolver(new ObjectMapper(), properties), properties);
    }

    private static RateLimitAspect rateLimit() {
        var properties = new RateLimitProperties();
        return new RateLimitAspect(new InMemoryRateLimitStore(),
                new DefaultRateLimitKeyResolver(properties), properties);
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
