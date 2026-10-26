package com.bingchunmoli.idempotency.autoconfigure;

import com.bingchunmoli.idempotency.annotation.Idempotent;
import com.bingchunmoli.idempotency.aspect.IdempotencyAspect;
import com.bingchunmoli.idempotency.core.IdempotencyStore;
import com.bingchunmoli.idempotency.core.IdempotencyToken;
import com.bingchunmoli.idempotency.exception.DuplicateRequestException;
import com.bingchunmoli.idempotency.store.InMemoryIdempotencyStore;
import com.bingchunmoli.idempotency.store.RedisIdempotencyStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

class IdempotencyAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AopAutoConfiguration.class,
                    RedisIdempotencyAutoConfiguration.class,
                    IdempotencyAutoConfiguration.class));

    @Test
    void preservesConcreteFutureReturnType() {
        contextRunner.withUserConfiguration(TestConfiguration.class).run(context -> {
            var service = context.getBean(TestService.class);
            var future = new CustomFuture<String>();
            assertThat(service.customFuture("custom", future)).isSameAs(future);
            future.complete("ok");
            assertThat(future.join()).isEqualTo("ok");
        });
    }

    @Test
    void cancellingReturnedFutureCancelsBusinessFutureAndReleasesKey() {
        contextRunner.withUserConfiguration(TestConfiguration.class).run(context -> {
            var service = context.getBean(TestService.class);
            var original = new CompletableFuture<String>();
            var returned = service.pendingFuture("cancel", original);
            assertThat(returned.cancel(false)).isTrue();
            assertThat(original.isCancelled()).isTrue();
            var retry = new CompletableFuture<String>();
            assertThat(service.pendingFuture("cancel", retry)).isSameAs(retry);
        });
    }

    @Test
    void overloadedDefaultNamespacesAreIndependent() {
        contextRunner.withUserConfiguration(TestConfiguration.class).run(context -> {
            var service = context.getBean(TestService.class);
            assertThat(service.overloaded("1")).isEqualTo("string");
            assertThat(service.overloaded(1L)).isEqualTo("long");
            assertThatThrownBy(() -> service.overloaded("1")).isInstanceOf(DuplicateRequestException.class);
            assertThatThrownBy(() -> service.overloaded(1L)).isInstanceOf(DuplicateRequestException.class);
        });
    }

    @Test
    void explicitNamespacesCanStillShareKeysAcrossOverloads() {
        contextRunner.withUserConfiguration(TestConfiguration.class).run(context -> {
            var service = context.getBean(TestService.class);
            service.shared("1");
            assertThatThrownBy(() -> service.shared(1L)).isInstanceOf(DuplicateRequestException.class);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cleanupFailuresPreserveBusinessException(boolean sameFailure) {
        var original = new IllegalArgumentException("business rejected");
        RuntimeException cleanup = sameFailure ? original : new IllegalStateException("release unavailable");
        var store = mock(IdempotencyStore.class);
        var token = new IdempotencyToken("test-key", "owner");
        when(store.tryAcquire(anyString(), any())).thenReturn(Optional.of(token));
        doThrow(cleanup).when(store).release(token);
        contextRunner.withBean(IdempotencyStore.class, () -> store)
                .withUserConfiguration(TestConfiguration.class).run(context -> {
                    assertThatThrownBy(() -> context.getBean(TestService.class).failWith("1", original))
                            .isSameAs(original);
                    if (sameFailure) assertThat(original.getSuppressed()).isEmpty();
                    else assertThat(original.getSuppressed()).containsExactly(cleanup);
                    verify(store).release(token);
                });
    }

    @Test
    void asynchronousCleanupFailuresPreserveTheOriginalCause() {
        var cleanup = new IllegalStateException("release unavailable");
        var store = mock(IdempotencyStore.class);
        var token = new IdempotencyToken("test-key", "owner");
        when(store.tryAcquire(anyString(), any())).thenReturn(Optional.of(token));
        doThrow(cleanup).when(store).release(token);
        contextRunner.withBean(IdempotencyStore.class, () -> store)
                .withUserConfiguration(TestConfiguration.class).run(context -> {
                    assertThatThrownBy(() -> context.getBean(TestService.class).failAsyncOnce("1").join())
                            .isInstanceOf(CompletionException.class)
                            .satisfies(failure -> {
                                assertThat(failure.getCause()).isInstanceOf(IllegalStateException.class)
                                        .hasMessage("temporary async failure");
                                assertThat(failure.getCause().getSuppressed()).contains(cleanup);
                            });
                });
    }

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

    @Test
    void rejectsInvalidDefaultsAtStartupButIgnoresDisabledConfiguration() {
        for (String property : new String[]{"moli.idempotency.default-timeout=0s",
                "moli.idempotency.default-timeout=-1s", "moli.idempotency.key-prefix="}) {
            contextRunner.withPropertyValues(property).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
            });
            contextRunner.withPropertyValues(property, "moli.idempotency.enabled=false")
                    .run(context -> assertThat(context).hasNotFailed());
        }
    }

    @Test
    void defaultDigestSupportsJavaTimeArgumentsWithoutAHostJackson2Mapper() {
        contextRunner.withUserConfiguration(TestConfiguration.class).run(context -> {
            TestService service = context.getBean(TestService.class);
            var timestamp = java.time.LocalDateTime.of(2026, 10, 22, 12, 0);
            assertThat(service.createTimed(timestamp)).isEqualTo(1);
            assertThatThrownBy(() -> service.createTimed(timestamp)).isInstanceOf(DuplicateRequestException.class);
            assertThat(service.createTimed(timestamp.plusSeconds(1))).isEqualTo(2);
        });
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

        @Idempotent(key = "#p0")
        public CustomFuture<String> customFuture(String key, CustomFuture<String> future) { return future; }

        @Idempotent(key = "#p0")
        public CompletableFuture<String> pendingFuture(String key, CompletableFuture<String> future) { return future; }

        @Idempotent(key = "#p0")
        public String overloaded(String value) { return "string"; }

        @Idempotent(key = "#p0")
        public String overloaded(Long value) { return "long"; }

        @Idempotent(key = "#p0", namespace = "shared")
        public String shared(String value) { return "string"; }

        @Idempotent(key = "#p0", namespace = "shared")
        public String shared(Long value) { return "long"; }

        @Idempotent(key = "#p0")
        public void failWith(String key, RuntimeException failure) { throw failure; }

        @Idempotent(key = "#p0", namespace = "order:create")
        public int create(String orderNo) {
            return creations.incrementAndGet();
        }

        @Idempotent
        public int createTimed(java.time.LocalDateTime timestamp) {
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

    static class CustomFuture<T> extends CompletableFuture<T> { }
}
