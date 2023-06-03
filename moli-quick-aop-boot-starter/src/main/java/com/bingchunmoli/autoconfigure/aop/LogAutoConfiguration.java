package com.bingchunmoli.autoconfigure.aop;

import com.bingchunmoli.aspect.LogAspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Import;

/**
 * @author MoLi
 */

@Import(LogAspect.class)
@ConditionalOnClass(LogAutoConfiguration.class)
@ConditionalOnMissingBean(LogAutoConfiguration.class)
public class LogAutoConfiguration {


}
