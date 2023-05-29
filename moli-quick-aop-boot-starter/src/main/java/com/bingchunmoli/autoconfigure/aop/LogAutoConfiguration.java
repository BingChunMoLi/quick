package com.bingchunmoli.autoconfigure.aop;

import com.bingchunmoli.aspect.LogAspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author MoLi
 */
@Configuration
@ConditionalOnClass(LogAutoConfiguration.class)
@ConditionalOnMissingBean(LogAutoConfiguration.class)
public class LogAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LogAspect.class)
    public LogAspect logAspect(){
        return new LogAspect();
    }
}
