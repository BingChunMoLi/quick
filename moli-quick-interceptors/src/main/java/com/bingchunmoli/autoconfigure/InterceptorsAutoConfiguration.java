package com.bingchunmoli.autoconfigure;

import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@Import(SignAutoConfiguration.class)
@EnableConfigurationProperties(InterceptorsAutoConfigurationProperties.class)
public class InterceptorsAutoConfiguration {

}
