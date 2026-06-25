package com.bingchunmoli.aspect;

import com.bingchunmoli.annotation.ExecutionTime;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionAspectTest {

    private final ExecutionAspect executionAspect = new ExecutionAspect();

    @Test
    void logThrowingShouldReturnProceedResult() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        ExecutionTime executionTime = mock(ExecutionTime.class);
        Object[] args = {"arg"};
        when(executionTime.value()).thenReturn("demo");
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.proceed(args)).thenReturn("ok");

        Object result = executionAspect.logThrowing(joinPoint, executionTime);

        assertThat(result).isEqualTo("ok");
        verify(joinPoint).proceed(args);
    }

    @Test
    void logThrowingShouldPropagateException() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        ExecutionTime executionTime = mock(ExecutionTime.class);
        Object[] args = new Object[0];
        RuntimeException exception = new RuntimeException("boom");
        when(executionTime.value()).thenReturn("demo");
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.proceed(args)).thenThrow(exception);

        assertThatThrownBy(() -> executionAspect.logThrowing(joinPoint, executionTime))
                .isSameAs(exception);
    }
}
