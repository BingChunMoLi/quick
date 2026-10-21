package com.bingchunmoli.ratelimit.autoconfigure;

import com.bingchunmoli.ratelimit.annotation.RateLimit;
import com.bingchunmoli.ratelimit.exception.RateLimitExceededException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

class InterfaceRateLimitTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void interfaceAnnotationsEnforceTheQuotaAndKeepKeysIndependent(boolean cglib) {
        run(cglib, Operations.class, Service::new, service -> {
            assertThat(service.call("one")).isEqualTo(1);
            assertThatThrownBy(() -> service.call("one")).isInstanceOf(RateLimitExceededException.class);
            assertThat(service.call("two")).isEqualTo(2);
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void inheritedInterfaceAnnotationsAreFound(boolean cglib) {
        run(cglib, ChildOperations.class, InheritedService::new, service -> {
            service.call("one");
            assertThatThrownBy(() -> service.call("one")).isInstanceOf(RateLimitExceededException.class);
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void genericBridgeMethodsAreProtected(boolean cglib) {
        run(cglib, GenericOperations.class, GenericService::new, service -> {
            service.call("one");
            assertThatThrownBy(() -> service.call("one")).isInstanceOf(RateLimitExceededException.class);
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void implementationAnnotationOverridesTheInterfaceWithoutDoubleCounting(boolean cglib) {
        run(cglib, Operations.class, OverrideService::new, service -> {
            assertThat(service.call("one")).isEqualTo(1);
            assertThat(service.call("one")).isEqualTo(2);
            assertThatThrownBy(() -> service.call("one"))
                    .isInstanceOfSatisfying(RateLimitExceededException.class, error -> {
                        assertThat(error.getPermits()).isEqualTo(2);
                        assertThat(error.getKey()).contains(":implementation:");
                    });
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void unannotatedMethodsOnAProtectedBeanAreNotLimited(boolean cglib) {
        run(cglib, Operations.class, Service::new, service -> {
            assertThat(service.plain("same")).isEqualTo(1);
            assertThat(service.plain("same")).isEqualTo(2);
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void unrelatedBeansAreNotProxied(boolean cglib) {
        runner(cglib).withBean("service", PlainService.class, PlainService::new).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(AopUtils.isAopProxy(context.getBean(PlainService.class))).isFalse();
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void inheritedImplementationFindsInterfacesIntroducedByTheTarget(boolean cglib) {
        run(cglib, ChildOperations.class, UnrelatedInheritedService::new, service -> {
            assertThat(service.call("one")).isEqualTo(1);
            assertThatThrownBy(() -> service.call("one")).isInstanceOf(RateLimitExceededException.class);
            assertThat(service.call("two")).isEqualTo(2);
            assertThat(service.plain("one")).isEqualTo(3);
            assertThat(service.plain("one")).isEqualTo(4);
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void genericImplementationInheritedFromAnUnrelatedBaseRemainsProtected(boolean cglib) {
        run(cglib, GenericOperations.class, GenericInheritedService::new, service -> {
            assertThat(service.call("one")).isEqualTo(1);
            assertThatThrownBy(() -> service.call("one")).isInstanceOf(RateLimitExceededException.class);
            assertThat(service.call("two")).isEqualTo(2);
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aConcreteInterfaceMatchesTheBridgeToItsGenericBaseImplementation(boolean cglib) {
        run(cglib, Operations.class, ConcreteGenericInheritedService::new, service -> {
            assertThat(service.call("one")).isEqualTo(1);
            assertThatThrownBy(() -> service.call("one")).isInstanceOf(RateLimitExceededException.class);
            assertThat(service.call("two")).isEqualTo(2);
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void interfaceLookupDoesNotProtectADifferentInheritedOverload(boolean cglib) {
        run(cglib, OverloadedOperations.class, OverloadedInheritedService::new, service -> {
            assertThat(service.call(1)).isEqualTo(1);
            assertThat(service.call(1)).isEqualTo(2);
            assertThat(service.call("one")).isEqualTo(3);
            assertThatThrownBy(() -> service.call("one")).isInstanceOf(RateLimitExceededException.class);
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void anInheritedImplementationAnnotationStillOverridesTheTargetInterface(boolean cglib) {
        run(cglib, Operations.class, AnnotatedBaseService::new, service -> {
            assertThat(service.call("one")).isEqualTo(1);
            assertThat(service.call("one")).isEqualTo(2);
            assertThatThrownBy(() -> service.call("one"))
                    .isInstanceOfSatisfying(RateLimitExceededException.class, error -> {
                        assertThat(error.getPermits()).isEqualTo(2);
                        assertThat(error.getKey()).contains(":parent-implementation:");
                    });
        });
    }

    private static ApplicationContextRunner runner(boolean cglib) {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                AopAutoConfiguration.class, RateLimitAutoConfiguration.class))
                .withPropertyValues("spring.aop.proxy-target-class=" + cglib);
    }

    private static <T> void run(boolean cglib, Class<T> type, Supplier<T> supplier, Consumer<T> assertions) {
        runner(cglib).withBean("service", type, supplier).run(context -> {
            assertThat(context).hasNotFailed();
            T service = context.getBean("service", type);
            assertThat(cglib ? AopUtils.isCglibProxy(service) : AopUtils.isJdkDynamicProxy(service)).isTrue();
            assertions.accept(service);
        });
    }

    public interface Operations {
        @RateLimit(key = "#p0", permits = 1, window = 60) int call(String key);
        int plain(String key);
    }
    public interface ChildOperations extends Operations { }
    public static class Service implements Operations {
        private final AtomicInteger calls = new AtomicInteger();
        public int call(String key) { return calls.incrementAndGet(); }
        public int plain(String key) { return calls.incrementAndGet(); }
    }
    public static class InheritedService extends Service implements ChildOperations { }
    public static class UnrelatedBase {
        protected final AtomicInteger calls = new AtomicInteger();
        public int call(String key) { return calls.incrementAndGet(); }
        public int plain(String key) { return calls.incrementAndGet(); }
    }
    public static class UnrelatedInheritedService extends UnrelatedBase implements ChildOperations { }
    public static class GenericUnrelatedBase<T> {
        private final AtomicInteger calls = new AtomicInteger();
        public int call(T key) { return calls.incrementAndGet(); }
        public int plain(String key) { return calls.incrementAndGet(); }
    }
    public static class GenericInheritedService extends GenericUnrelatedBase<String>
            implements GenericOperations<String> { }
    public static class ConcreteGenericInheritedService extends GenericUnrelatedBase<String> implements Operations { }
    public interface OverloadedOperations extends Operations { int call(Integer key); }
    public static class OverloadedBase extends UnrelatedBase {
        public int call(Integer key) { return calls.incrementAndGet(); }
    }
    public static class OverloadedInheritedService extends OverloadedBase implements OverloadedOperations { }
    public static class AnnotatedBase extends UnrelatedBase {
        @Override @RateLimit(namespace = "parent-implementation", key = "#p0", permits = 2, window = 60)
        public int call(String key) { return super.call(key); }
    }
    public static class AnnotatedBaseService extends AnnotatedBase implements Operations { }
    public static class OverrideService extends Service {
        @Override @RateLimit(namespace = "implementation", key = "#p0", permits = 2, window = 60)
        public int call(String key) { return super.call(key); }
    }
    public interface GenericOperations<T> { @RateLimit(key = "#p0", permits = 1, window = 60) int call(T key); }
    public static class GenericService implements GenericOperations<String> { public int call(String key) { return 1; } }
    public static class PlainService { public String call(String key) { return key; } }
}
