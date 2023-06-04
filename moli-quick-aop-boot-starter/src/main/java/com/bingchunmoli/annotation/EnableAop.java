package com.bingchunmoli.annotation;

import com.bingchunmoli.aspect.ExecutionAspect;
import com.bingchunmoli.aspect.LogAspect;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 导入所有aop注解处理器
 * @author MoLi
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({LogAspect.class, ExecutionAspect.class})
public @interface EnableAop {
}
