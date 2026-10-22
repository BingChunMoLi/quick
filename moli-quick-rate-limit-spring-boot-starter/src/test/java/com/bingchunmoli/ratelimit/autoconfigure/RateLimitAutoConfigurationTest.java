package com.bingchunmoli.ratelimit.autoconfigure;

import com.bingchunmoli.ratelimit.annotation.RateLimit;
import com.bingchunmoli.ratelimit.aspect.RateLimitAspect;
import com.bingchunmoli.ratelimit.core.RateLimitStore;
import com.bingchunmoli.ratelimit.exception.RateLimitExceededException;
import com.bingchunmoli.ratelimit.store.RedisRateLimitStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RateLimitAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AopAutoConfiguration.class,
                    RedisRateLimitAutoConfiguration.class,
                    RateLimitAutoConfiguration.class));

    @Test
    void overloadedMethodsHaveIndependentDefaultBuckets() {
        contextRunner.withUserConfiguration(TestConfiguration.class).run(context -> {
            var service = context.getBean(TestService.class);
            assertThat(service.overloaded("1")).isEqualTo("string");
            assertThat(service.overloaded(1L)).isEqualTo("long");
            assertThatThrownBy(() -> service.overloaded("2")).isInstanceOf(RateLimitExceededException.class);
            assertThatThrownBy(() -> service.overloaded(2L)).isInstanceOf(RateLimitExceededException.class);
        });
    }

    @Test
    void explicitNamespacesStillShareBucketsAcrossOverloads() {
        contextRunner.withUserConfiguration(TestConfiguration.class).run(context -> {
            var service = context.getBean(TestService.class);
            service.shared("1");
            assertThatThrownBy(() -> service.shared(1L)).isInstanceOf(RateLimitExceededException.class);
        });
    }

    @Test
    void nonOverloadedDefaultBucketKeepsItsExistingKey() {
        contextRunner.withUserConfiguration(TestConfiguration.class).run(context -> {
            var service = context.getBean(TestService.class);
            service.single("1");
            assertThatThrownBy(() -> service.single("2"))
                    .isInstanceOfSatisfying(RateLimitExceededException.class, failure ->
                            assertThat(failure.getKey()).isEqualTo(
                                    "moli:rate-limit:" + TestService.class.getName() + ".single:global"));
        });
    }

    @Test
    void shouldLimitEachResolvedKeyIndependently() {
        contextRunner.withUserConfiguration(TestConfiguration.class).run(context -> {
            TestService service = context.getBean(TestService.class);

            assertThat(service.userRequest("user-1")).isEqualTo(1);
            assertThat(service.userRequest("user-1")).isEqualTo(2);
            assertThatThrownBy(() -> service.userRequest("user-1"))
                    .isInstanceOfSatisfying(RateLimitExceededException.class, exception -> {
                        assertThat(exception.getMessage()).isEqualTo("Please retry later");
                        assertThat(exception.getKey()).isEqualTo("moli:rate-limit:api:user:user-1");
                        assertThat(exception.getPermits()).isEqualTo(2);
                        assertThat(exception.getRetryAfter()).isPositive();
                    });
            assertThat(service.userRequest("user-2")).isEqualTo(3);
        });
    }

    @Test
    void shouldUseConfiguredDefaultsAndGlobalMethodBucket() {
        contextRunner
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues(
                        "moli.rate-limit.default-permits=1",
                        "moli.rate-limit.default-window=30s")
                .run(context -> {
                    TestService service = context.getBean(TestService.class);

                    assertThat(service.globalRequest("first")).isEqualTo("first");
                    assertThatThrownBy(() -> service.globalRequest("different-argument"))
                            .isInstanceOf(RateLimitExceededException.class);
                });
    }

    @Test
    void shouldUseRedisStoreWhenSelected() {
        contextRunner
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withPropertyValues("moli.rate-limit.store=redis")
                .run(context -> assertThat(context).getBean(RateLimitStore.class)
                        .isInstanceOf(RedisRateLimitStore.class));
    }

    @Test
    void shouldFailFastWhenRedisIsSelectedWithoutTemplate() {
        contextRunner.withPropertyValues("moli.rate-limit.store=redis")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "moli.rate-limit.store=redis requires an auto-configured StringRedisTemplate");
                });
    }

    @Test
    void shouldBackOffForCustomStore() {
        RateLimitStore custom = mock(RateLimitStore.class);
        contextRunner.withBean(RateLimitStore.class, () -> custom)
                .run(context -> assertThat(context.getBean(RateLimitStore.class)).isSameAs(custom));
    }

    @Test
    void shouldNotConfigureWhenDisabled() {
        contextRunner.withPropertyValues("moli.rate-limit.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RateLimitAspect.class);
                    assertThat(context).doesNotHaveBean(RateLimitStore.class);
                });
    }

    @Test
    void validatesDefaultsAtStartupAndDoesNotValidateDisabledConfiguration() {
        for (String property : new String[]{"moli.rate-limit.default-permits=0", "moli.rate-limit.default-window=0s",
                "moli.rate-limit.default-window=-1s", "moli.rate-limit.key-prefix="}) {
            contextRunner.withPropertyValues(property).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
            });
            contextRunner.withPropertyValues(property, "moli.rate-limit.enabled=false")
                    .run(context -> assertThat(context).hasNotFailed());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {

        @Bean
        TestService testService() {
            return new TestService();
        }
    }

    static class TestService {

        private final AtomicInteger requests = new AtomicInteger();

        @RateLimit(permits = 1)
        public String overloaded(String value) { return "string"; }

        @RateLimit(permits = 1)
        public String overloaded(Long value) { return "long"; }

        @RateLimit(permits = 1, namespace = "shared")
        public String shared(String value) { return "string"; }

        @RateLimit(permits = 1, namespace = "shared")
        public String shared(Long value) { return "long"; }

        @RateLimit(permits = 1)
        public String single(String value) { return value; }

        @RateLimit(
                key = "#p0",
                namespace = "api:user",
                permits = 2,
                window = 1,
                timeUnit = TimeUnit.MINUTES,
                message = "Please retry later")
        public int userRequest(String userId) {
            return requests.incrementAndGet();
        }

        @RateLimit(namespace = "api:global")
        public String globalRequest(String argument) {
            return argument;
        }
    }
}
