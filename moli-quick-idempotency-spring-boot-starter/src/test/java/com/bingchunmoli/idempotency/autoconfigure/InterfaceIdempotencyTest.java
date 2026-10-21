package com.bingchunmoli.idempotency.autoconfigure;

import com.bingchunmoli.idempotency.annotation.Idempotent;
import com.bingchunmoli.idempotency.exception.DuplicateRequestException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

class InterfaceIdempotencyTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void interfaceAnnotationsRejectDuplicates(boolean cglib) {
        run(cglib, Operations.class, Service::new, service -> {
            assertThat(service.create("one")).isEqualTo(1);
            assertThatThrownBy(() -> service.create("one")).isInstanceOf(DuplicateRequestException.class);
            assertThat(service.create("two")).isEqualTo(2);
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void inheritedInterfaceAnnotationsAreFound(boolean cglib) {
        run(cglib, ChildOperations.class, InheritedService::new, service -> {
            service.create("one");
            assertThatThrownBy(() -> service.create("one")).isInstanceOf(DuplicateRequestException.class);
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void genericInterfaceBridgeMethodsRemainProtected(boolean cglib) {
        run(cglib, GenericOperations.class, GenericService::new, service -> {
            service.create("one");
            assertThatThrownBy(() -> service.create("one")).isInstanceOf(DuplicateRequestException.class);
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void implementationAnnotationOverridesTheInterfaceWithoutDoubleAcquiring(boolean cglib) {
        run(cglib, Operations.class, OverrideService::new, service -> {
            assertThat(service.create("one")).isEqualTo(1);
            assertThatThrownBy(() -> service.create("one"))
                    .isInstanceOfSatisfying(DuplicateRequestException.class,
                            error -> assertThat(error.getIdempotencyKey()).contains(":implementation:"));
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void unannotatedMethodsOnAProtectedBeanAreNotIntercepted(boolean cglib) {
        run(cglib, Operations.class, Service::new, service -> {
            assertThat(service.plain("same")).isEqualTo(1);
            assertThat(service.plain("same")).isEqualTo(2);
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aBeanWithoutAnnotationsIsNotProxied(boolean cglib) {
        runner(cglib).withBean("service", PlainService.class, PlainService::new).run(context -> {
            assertThat(context).hasNotFailed();
            var service = context.getBean(PlainService.class);
            assertThat(AopUtils.isAopProxy(service)).isFalse();
            assertThat(service.call("same")).isEqualTo("same");
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void asyncInterfaceFailureReleasesTheKeyForRetry(boolean cglib) {
        run(cglib, AsyncOperations.class, AsyncService::new, service -> {
            assertThatThrownBy(() -> service.create("one").join()).isInstanceOf(CompletionException.class);
            assertThat(service.create("one").join()).isEqualTo("ok");
            assertThatThrownBy(() -> service.create("one")).isInstanceOf(DuplicateRequestException.class);
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void inheritedImplementationFindsInterfacesIntroducedByTheTarget(boolean cglib) {
        run(cglib, ChildOperations.class, UnrelatedInheritedService::new, service -> {
            assertThat(service.create("one")).isEqualTo(1);
            assertThatThrownBy(() -> service.create("one")).isInstanceOf(DuplicateRequestException.class);
            assertThat(service.create("two")).isEqualTo(2);
            assertThat(service.plain("one")).isEqualTo(3);
            assertThat(service.plain("one")).isEqualTo(4);
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void genericImplementationInheritedFromAnUnrelatedBaseRemainsProtected(boolean cglib) {
        run(cglib, GenericOperations.class, GenericInheritedService::new, service -> {
            assertThat(service.create("one")).isEqualTo(1);
            assertThatThrownBy(() -> service.create("one")).isInstanceOf(DuplicateRequestException.class);
            assertThat(service.create("two")).isEqualTo(2);
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aConcreteInterfaceMatchesTheBridgeToItsGenericBaseImplementation(boolean cglib) {
        run(cglib, Operations.class, ConcreteGenericInheritedService::new, service -> {
            assertThat(service.create("one")).isEqualTo(1);
            assertThatThrownBy(() -> service.create("one")).isInstanceOf(DuplicateRequestException.class);
            assertThat(service.create("two")).isEqualTo(2);
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void interfaceLookupDoesNotProtectADifferentInheritedOverload(boolean cglib) {
        run(cglib, OverloadedOperations.class, OverloadedInheritedService::new, service -> {
            assertThat(service.create(1)).isEqualTo(1);
            assertThat(service.create(1)).isEqualTo(2);
            assertThat(service.create("one")).isEqualTo(3);
            assertThatThrownBy(() -> service.create("one")).isInstanceOf(DuplicateRequestException.class);
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void anInheritedImplementationAnnotationStillOverridesTheTargetInterface(boolean cglib) {
        run(cglib, Operations.class, AnnotatedBaseService::new, service -> {
            assertThat(service.create("one")).isEqualTo(1);
            assertThatThrownBy(() -> service.create("one"))
                    .isInstanceOfSatisfying(DuplicateRequestException.class,
                            error -> assertThat(error.getIdempotencyKey()).contains(":parent-implementation:"));
        });
    }

    private static ApplicationContextRunner runner(boolean cglib) {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                AopAutoConfiguration.class, IdempotencyAutoConfiguration.class))
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
        @Idempotent(key = "#p0", timeout = 60) int create(String key);
        int plain(String key);
    }
    public interface ChildOperations extends Operations { }
    public static class Service implements Operations {
        private final AtomicInteger calls = new AtomicInteger();
        public int create(String key) { return calls.incrementAndGet(); }
        public int plain(String key) { return calls.incrementAndGet(); }
    }
    public static class InheritedService extends Service implements ChildOperations { }
    public static class UnrelatedBase {
        protected final AtomicInteger calls = new AtomicInteger();
        public int create(String key) { return calls.incrementAndGet(); }
        public int plain(String key) { return calls.incrementAndGet(); }
    }
    public static class UnrelatedInheritedService extends UnrelatedBase implements ChildOperations { }
    public static class GenericUnrelatedBase<T> {
        private final AtomicInteger calls = new AtomicInteger();
        public int create(T key) { return calls.incrementAndGet(); }
        public int plain(String key) { return calls.incrementAndGet(); }
    }
    public static class GenericInheritedService extends GenericUnrelatedBase<String>
            implements GenericOperations<String> { }
    public static class ConcreteGenericInheritedService extends GenericUnrelatedBase<String> implements Operations { }
    public interface OverloadedOperations extends Operations { int create(Integer key); }
    public static class OverloadedBase extends UnrelatedBase {
        public int create(Integer key) { return calls.incrementAndGet(); }
    }
    public static class OverloadedInheritedService extends OverloadedBase implements OverloadedOperations { }
    public static class AnnotatedBase extends UnrelatedBase {
        @Override @Idempotent(namespace = "parent-implementation", key = "#p0", timeout = 60)
        public int create(String key) { return super.create(key); }
    }
    public static class AnnotatedBaseService extends AnnotatedBase implements Operations { }
    public static class OverrideService extends Service {
        @Override @Idempotent(namespace = "implementation", key = "#p0", timeout = 60)
        public int create(String key) { return super.create(key); }
    }
    public interface GenericOperations<T> {
        @Idempotent(key = "#p0", timeout = 60) int create(T key);
    }
    public static class GenericService implements GenericOperations<String> {
        public int create(String key) { return 1; }
    }
    public static class PlainService { public String call(String key) { return key; } }
    public interface AsyncOperations {
        @Idempotent(key = "#p0", timeout = 60) CompletableFuture<String> create(String key);
    }
    public static class AsyncService implements AsyncOperations {
        private final AtomicInteger calls = new AtomicInteger();
        public CompletableFuture<String> create(String key) {
            return calls.incrementAndGet() == 1 ? CompletableFuture.failedFuture(new IllegalStateException("retry"))
                    : CompletableFuture.completedFuture("ok");
        }
    }
}
