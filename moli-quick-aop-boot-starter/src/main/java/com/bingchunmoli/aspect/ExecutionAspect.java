package com.bingchunmoli.aspect;

import com.bingchunmoli.annotation.ExecutionTime;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.util.StopWatch;
import org.springframework.core.annotation.AnnotationUtils;

/**
 * @author MoLi
 */
@Slf4j
@Aspect
public class ExecutionAspect {
    @Around(value = "@annotation(executionTime)" )
    public Object logThrowing(ProceedingJoinPoint pjp, ExecutionTime executionTime) throws Throwable{
        if (!log.isDebugEnabled()) {
            return pjp.proceed(pjp.getArgs());
        }
        StopWatch stopWatch = new StopWatch();
        stopWatch.start(AnnotationUtils.synthesizeAnnotation(executionTime, null).value());
        try {
            return pjp.proceed(pjp.getArgs());
        }finally {
            stopWatch.stop();
            if (log.isDebugEnabled()) {
                log.debug(stopWatch.prettyPrint());
            }
        }
    }
}
