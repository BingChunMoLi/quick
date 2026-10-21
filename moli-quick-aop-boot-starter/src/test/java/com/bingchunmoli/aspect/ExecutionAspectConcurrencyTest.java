package com.bingchunmoli.aspect;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bingchunmoli.annotation.ExecutionTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@ResourceLock("ExecutionAspect logger")
class ExecutionAspectConcurrencyTest {
    private Logger logger;
    private Level previousLevel;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void saveLoggerLevel() {
        logger = (Logger) LoggerFactory.getLogger(ExecutionAspect.class);
        previousLevel = logger.getLevel();
    }

    @AfterEach
    void restoreLogger() {
        if (appender != null) {
            logger.detachAppender(appender);
            appender.stop();
        }
        logger.setLevel(previousLevel);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void concurrentInvocationsEnterTheBusinessMethodWithoutSerialization(boolean debug) throws Exception {
        logger.setLevel(debug ? Level.DEBUG : Level.INFO);
        var target = new Work();
        var service = proxy(target, new ExecutionAspect());
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> service.blocked(entered, release));
            var second = pool.submit(() -> service.blocked(entered, release));
            assertThat(entered.await(5, TimeUnit.SECONDS)).as("both business calls entered before either returned").isTrue();
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("ok");
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("ok");
            assertThat(target.blockedCalls.get()).isEqualTo(2);
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void crossBeanNestedInvocationsCompleteNormally(boolean debug) {
        logger.setLevel(debug ? Level.DEBUG : Level.INFO);
        var aspect = new ExecutionAspect();
        var innerTarget = new Inner();
        var outer = proxy(new Outer(proxy(innerTarget, aspect)), aspect);
        assertThat(outer.execute()).isEqualTo("ok");
        assertThat(innerTarget.calls.get()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void nestedFailuresKeepTheOriginalExceptionAndDoNotPoisonTheNextCall(boolean debug) {
        logger.setLevel(debug ? Level.DEBUG : Level.INFO);
        var innerTarget = new Inner();
        var failure = new IllegalArgumentException("business failure");
        innerTarget.failure = failure;
        var inner = proxy(innerTarget, new ExecutionAspect());
        var outer = proxy(new Outer(inner), new ExecutionAspect());
        assertThatThrownBy(outer::execute).isSameAs(failure);
        innerTarget.failure = null;
        assertThat(outer.execute()).isEqualTo("ok");
        assertThat(innerTarget.calls.get()).isEqualTo(2);
    }

    @Test
    void sequentialDebugMessagesOnlyDescribeTheCurrentInvocation() {
        logger.setLevel(Level.DEBUG);
        captureLogs();
        var target = new Work();
        var service = proxy(target, new ExecutionAspect());
        for (int i = 0; i < 100; i++) {
            assertThat(service.quick()).isEqualTo("ok");
        }
        assertThat(target.quickCalls.get()).isEqualTo(100);
        assertThat(appender.list).hasSize(100).allSatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
            // A global task history would repeat "quick" once for every prior invocation.
            assertThat(event.getFormattedMessage().split("quick", -1)).hasSize(2);
        });
    }

    @Test
    void disabledDebugLoggingLeavesBusinessResultsUntouchedAndEmitsNoTimingEvents() {
        logger.setLevel(Level.INFO);
        captureLogs();
        var target = new Work();
        var service = proxy(target, new ExecutionAspect());
        for (int i = 0; i < 100; i++) {
            assertThat(service.quick()).isEqualTo("ok");
        }
        assertThat(target.quickCalls.get()).isEqualTo(100);
        assertThat(appender.list).isEmpty();
    }

    private void captureLogs() {
        appender = new ListAppender<>();
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
    }

    private static <T> T proxy(T target, ExecutionAspect aspect) {
        var factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    public static class Work {
        final AtomicInteger blockedCalls = new AtomicInteger();
        final AtomicInteger quickCalls = new AtomicInteger();
        @ExecutionTime("blocked")
        public String blocked(CountDownLatch entered, CountDownLatch release) throws InterruptedException {
            blockedCalls.incrementAndGet();
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("business call was never released");
            }
            return "ok";
        }
        @ExecutionTime("quick")
        public String quick() { quickCalls.incrementAndGet(); return "ok"; }
    }

    public static class Inner {
        final AtomicInteger calls = new AtomicInteger();
        RuntimeException failure;
        @ExecutionTime("inner")
        public String execute() {
            calls.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return "ok";
        }
    }

    public static class Outer {
        private final Inner inner;
        Outer(Inner inner) { this.inner = inner; }
        @ExecutionTime("outer")
        public String execute() { return inner.execute(); }
    }
}
