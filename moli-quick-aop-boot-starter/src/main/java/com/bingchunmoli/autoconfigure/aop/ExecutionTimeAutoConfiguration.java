package com.bingchunmoli.autoconfigure.aop;

import com.bingchunmoli.aspect.ExecutionAspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author MoLi
 */
@Configuration
@ConditionalOnClass(ExecutionTimeAutoConfiguration.class)
@ConditionalOnMissingBean(ExecutionTimeAutoConfiguration.class)
public class ExecutionTimeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ExecutionAspect.class)
    public ExecutionAspect executionAspect(){
        return new ExecutionAspect();
    }
}
