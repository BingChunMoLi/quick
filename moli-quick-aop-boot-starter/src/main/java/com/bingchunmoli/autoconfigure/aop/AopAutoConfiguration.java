package com.bingchunmoli.autoconfigure.aop;

import org.springframework.context.annotation.Import;

/**
 * @author MoLi
 */
@Import({LogAutoConfiguration.class, ExecutionTimeAutoConfiguration.class})
public class AopAutoConfiguration {
}
