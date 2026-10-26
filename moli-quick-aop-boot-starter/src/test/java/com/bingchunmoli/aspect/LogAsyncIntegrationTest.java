package com.bingchunmoli.aspect;

import com.bingchunmoli.annotation.EnableLog;
import com.bingchunmoli.annotation.Log;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.EnableAsync;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;

@ResourceLock("ExecutionAspect logger")
class LogAsyncIntegrationTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(LogAspect.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private Level previousLevel;
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class))
            .withUserConfiguration(LoggingConfiguration.class)
            .withBean("taskExecutor", TaskExecutor.class, () -> task -> {
                throw new TaskRejectedException("Executor unavailable");
            });

    @BeforeEach
    void capture() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void restore() {
        logger.detachAppender(events);
        events.stop();
        logger.setLevel(previousLevel);
    }

    @Test
    void rejectedLoggingMustPreserveSuccessfulBusinessResult() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(Work.class).save()).isEqualTo("saved");
            assertThat(events.list).singleElement().satisfies(event ->
                    assertThat(event.getFormattedMessage()).contains("其他信息: save"));
        });
    }

    @Test
    void rejectedLoggingMustPreserveBusinessException() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThatThrownBy(() -> context.getBean(Work.class).fail()).isSameAs(Work.FAILURE);
            assertThat(events.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getThrowableProxy().getMessage()).isEqualTo("business failure");
            });
        });
    }

    @Test
    void enabledAsyncStillSubmitsLoggingToTheHostExecutor() {
        var tasks = new ArrayList<Runnable>();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class))
                .withUserConfiguration(LoggingConfiguration.class)
                .withBean("taskExecutor", TaskExecutor.class, () -> tasks::add)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(Work.class).save()).isEqualTo("saved");
                    assertThat(tasks).hasSize(1);
                    assertThat(events.list).isEmpty();
                    tasks.get(0).run();
                    assertThat(events.list).hasSize(1);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableLog
    @EnableAsync(proxyTargetClass = true)
    static class LoggingConfiguration {
        @Bean Work work() { return new Work(); }
    }

    static class Work {
        static final RuntimeException FAILURE = new IllegalStateException("business failure");
        @Log("save") public String save() { return "saved"; }
        @Log("failure") public void fail() { throw FAILURE; }
    }
}
