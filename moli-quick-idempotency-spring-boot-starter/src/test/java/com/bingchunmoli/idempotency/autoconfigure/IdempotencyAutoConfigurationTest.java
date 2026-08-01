package com.bingchunmoli.idempotency.autoconfigure;

import com.bingchunmoli.idempotency.annotation.Idempotent;
import com.bingchunmoli.idempotency.aspect.IdempotencyAspect;
import com.bingchunmoli.idempotency.core.IdempotencyStore;
import com.bingchunmoli.idempotency.exception.DuplicateRequestException;
import com.bingchunmoli.idempotency.store.InMemoryIdempotencyStore;
import com.bingchunmoli.idempotency.store.RedisIdempotencyStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class IdempotencyAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AopAutoConfiguration.class,
                    RedisIdempotencyAutoConfiguration.class,
                    IdempotencyAutoConfiguration.class));

    @Test
    void shouldRejectDuplicateAndAllowDifferentKey() {
        contextRunner.withUserConfiguration(TestConfiguration.class).run(context -> {
            TestService service = context.getBean(TestService.class);

            assertThat(service.create("order-1")).isEqualTo(1);
            assertThatThrownBy(() -> service.create("order-1"))
                    .isInstanceOf(DuplicateRequestException.class);
            assertThat(service.create("order-2")).isEqualTo(2);
        });
    }

    @Test
    void shouldReleaseKeyWhenInvocationFails() {
        contextRunner.withUserConfiguration(TestConfiguration.class).run(context -> {
            TestService service = context.getBean(TestService.class);

            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> service.failOnce("retryable"));
            assertThat(service.failOnce("retryable")).isEqualTo("ok");
        });
    }

    @Test
    void shouldReleaseKeyWhenCompletionStageFails() {
        contextRunner.withUserConfiguration(TestConfiguration.class).run(context -> {
            TestService service = context.getBean(TestService.class);

            assertThatThrownBy(() -> service.failAsyncOnce("async-retryable").join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
            assertThat(service.failAsyncOnce("async-retryable").join()).isEqualTo("ok");
        });
    }

    @Test
    void shouldNotConfigureWhenDisabled() {
        contextRunner.withPropertyValues("moli.idempotency.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(IdempotencyAspect.class);
                    assertThat(context).doesNotHaveBean(IdempotencyStore.class);
                });
    }

    @Test
    void shouldUseRedisStoreWhenSelected() {
        contextRunner
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withPropertyValues("moli.idempotency.store=redis")
                .run(context -> assertThat(context).getBean(IdempotencyStore.class)
                        .isInstanceOf(RedisIdempotencyStore.class));
    }

    @Test
    void shouldFailFastWhenRedisIsSelectedWithoutTemplate() {
        contextRunner.withPropertyValues("moli.idempotency.store=redis")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "moli.idempotency.store=redis requires an auto-configured StringRedisTemplate");
                });
    }

    @Test
    void shouldBackOffForCustomStore() {
        IdempotencyStore custom = mock(IdempotencyStore.class);
        contextRunner.withBean(IdempotencyStore.class, () -> custom)
                .run(context -> assertThat(context.getBean(IdempotencyStore.class)).isSameAs(custom));
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {

        @Bean
        TestService testService() {
            return new TestService();
        }
    }

    static class TestService {

        private final AtomicInteger creations = new AtomicInteger();
        private final AtomicInteger attempts = new AtomicInteger();
        private final AtomicInteger asyncAttempts = new AtomicInteger();

        @Idempotent(key = "#p0", namespace = "order:create")
        public int create(String orderNo) {
            return creations.incrementAndGet();
        }

        @Idempotent(key = "#p0", namespace = "order:retry")
        public String failOnce(String key) {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("temporary failure");
            }
            return "ok";
        }

        @Idempotent(key = "#p0", namespace = "order:async-retry")
        public CompletableFuture<String> failAsyncOnce(String key) {
            if (asyncAttempts.incrementAndGet() == 1) {
                return CompletableFuture.failedFuture(new IllegalStateException("temporary async failure"));
            }
            return CompletableFuture.completedFuture("ok");
        }
    }
}
