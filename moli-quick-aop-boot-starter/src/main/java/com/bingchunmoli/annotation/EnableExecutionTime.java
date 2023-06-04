package com.bingchunmoli.annotation;

import com.bingchunmoli.aspect.ExecutionAspect;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 导入execution注解处理器
 * @author MoLi
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ExecutionAspect.class)
public @interface EnableExecutionTime {
}
