package com.bingchunmoli.annotation;

import com.bingchunmoli.aspect.LogAspect;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 导入Log注解处理器
 * @author MoLi
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(LogAspect.class)
public @interface EnableLog {
}
