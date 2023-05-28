package com.bingchunmoli.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 方法执行时间统计
 * @author MoLi
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExecutionTime {

    /**
     * 任务名称同任务名称 name
     */
    @AliasFor("name")
    String value() default "";

    /**
     * 任务名称同value
     */
    @AliasFor("value")
    String name() default "";

    /**
     * 统计的时间单位(备用)
     */
    TimeUnit timeunit() default TimeUnit.NANOSECONDS;

}
