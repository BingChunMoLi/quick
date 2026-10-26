package com.bingchunmoli.aspect;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bingchunmoli.annotation.ExecutionTime;
import com.bingchunmoli.annotation.Log;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import static org.assertj.core.api.Assertions.*;

@ResourceLock("ExecutionAspect logger")
class AspectAnnotationAliasTest {
    private final Logger log=(Logger)LoggerFactory.getLogger(LogAspect.class);
    private final Logger timing=(Logger)LoggerFactory.getLogger(ExecutionAspect.class);
    private final ListAppender<ILoggingEvent> events=new ListAppender<>();
    private Level oldLog,oldTiming;
    private Work service;

    @BeforeEach void capture() {
        oldLog=log.getLevel();oldTiming=timing.getLevel();
        log.setLevel(Level.INFO);timing.setLevel(Level.DEBUG);
        events.start();log.addAppender(events);timing.addAppender(events);
        var factory=new AspectJProxyFactory(new Work());factory.setProxyTargetClass(true);
        factory.addAspect(new LogAspect());factory.addAspect(new ExecutionAspect());service=factory.getProxy();
    }
    @AfterEach void restore() {
        log.detachAppender(events);timing.detachAppender(events);events.stop();
        log.setLevel(oldLog);timing.setLevel(oldTiming);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void logDescriptionUsesEitherAlias(boolean shorthand) {
        assertThat(shorthand?service.shorthandLog():service.namedLog()).isEqualTo("saved");
        assertThat(events.list).singleElement().satisfies(event->assertThat(event.getFormattedMessage())
                .contains("其他信息: save-description", "模块名: orders"));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void failureLoggingResolvesAliasesAndPreservesBusinessException(boolean shorthand) {
        assertThatThrownBy(()->{if(shorthand)service.shorthandFailure();else service.namedFailure();})
                .isSameAs(Work.FAILURE);
        assertThat(events.list).singleElement().satisfies(event->{
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("其他信息: failure-description");
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("business failure");
        });
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void timingUsesEitherTaskNameAlias(boolean shorthand) {
        assertThat(shorthand?service.shorthandTiming():service.namedTiming()).isEqualTo("timed");
        assertThat(events.list).singleElement().satisfies(event->assertThat(event.getFormattedMessage()).contains("named-timing"));
    }

    public static class Work {
        static final RuntimeException FAILURE=new IllegalStateException("business failure");
        @Log(value="save-description",module="orders") public String shorthandLog(){return "saved";}
        @Log(info="save-description",module="orders") public String namedLog(){return "saved";}
        @Log("failure-description") public void shorthandFailure(){throw FAILURE;}
        @Log(info="failure-description") public void namedFailure(){throw FAILURE;}
        @ExecutionTime("named-timing") public String shorthandTiming(){return "timed";}
        @ExecutionTime(name="named-timing") public String namedTiming(){return "timed";}
    }
}
