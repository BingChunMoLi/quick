package com.bingchunmoli.aspect;

import com.bingchunmoli.annotation.ExecutionTime;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.util.StopWatch;

/**
 * @author MoLi
 */
@Slf4j
@Aspect
public class ExecutionAspect {
    private final static StopWatch STOP_WATCH = new StopWatch();

    @Around(value = "@annotation(executionTime)" )
    public Object logThrowing(ProceedingJoinPoint pjp, ExecutionTime executionTime) throws Throwable{
        STOP_WATCH.start(executionTime.value());
        try {
            return pjp.proceed(pjp.getArgs());
        }finally {
            STOP_WATCH.stop();
            log.debug(STOP_WATCH.prettyPrint());
        }
    }
}
