package com.bingchunmoli.aspect;

import com.bingchunmoli.annotation.Log;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.task.TaskRejectedException;

/**
 * @author MoLi
 */
@Slf4j
@Aspect
@Import(AsyncLogDispatcher.class)
public class LogAspect {

    private AsyncLogDispatcher dispatcher;

    @Autowired(required = false)
    void setDispatcher(AsyncLogDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @AfterReturning(pointcut = "@annotation(logging)", returning = "result")
    public void log(JoinPoint jp, Log logging, Object result){
        dispatch(() -> {
            if (log.isInfoEnabled()) {
                Log options = AnnotationUtils.synthesizeAnnotation(logging, null);
                log.info("模块名: {}, 其他信息: {}, 方法全类名: {}, 方法名: {}, 请求参数:{}, 返回值: {}", options.module(), options.info(), jp.getTarget().getClass().getName(), jp.getSignature().getName(), jp.getArgs(), result);
            }
        });
    }

    @AfterThrowing(pointcut = "@annotation(logging)", throwing = "e")
    public void logThrowing(JoinPoint jp, Log logging, Exception e){
        dispatch(() -> {
            if (log.isErrorEnabled()) {
                Log options = AnnotationUtils.synthesizeAnnotation(logging, null);
                log.error("模块名: {}, 其他信息: {}, 方法全类名: {}, 方法名: {}, 请求参数:{}, 异常信息", options.module(), options.info(), jp.getTarget().getClass().getName(), jp.getSignature().getName(), jp.getArgs(), e);
            }
        });
    }

    private void dispatch(Runnable action) {
        if (dispatcher == null) {
            action.run();
            return;
        }
        try {
            dispatcher.execute(action);
        } catch (TaskRejectedException rejected) {
            action.run();
        }
    }
}
