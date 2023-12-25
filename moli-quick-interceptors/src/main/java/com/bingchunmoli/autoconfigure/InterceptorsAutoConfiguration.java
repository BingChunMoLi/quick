package com.bingchunmoli.autoconfigure;

import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import com.bingchunmoli.filter.CacheFilter;
import com.bingchunmoli.interceptor.SignInterceptor;
import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import com.bingchunmoli.registrar.InterceptorsRegistrar;
import com.bingchunmoli.util.SHA256WithRSASignUtil;
import com.bingchunmoli.util.SignUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@RequiredArgsConstructor
@EnableConfigurationProperties(InterceptorsAutoConfigurationProperties.class)
public class InterceptorsAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "moli.interceptor.sign.sign", havingValue = "true")
    SignAutoConfiguration signAutoConfiguration() {
        return new SignAutoConfiguration();
    }

    @RequiredArgsConstructor
    @AutoConfiguration(after = {ObjectMapper.class, RedisUtil.class, InterceptorsAutoConfigurationProperties.class})
    @ConditionalOnProperty(prefix = "moli.interceptor.sign.sign", value = "enable", havingValue = "true")
    static class SignAutoConfiguration {

        @Bean
        SignUtil signUtil(InterceptorsAutoConfigurationProperties propertie, ObjectMapper om, RedisUtil redisUtil) {
            String algorithm = propertie.getSign().getAlgorithm();
            return switch (algorithm) {
                case "SHA256WithRSA" -> new SHA256WithRSASignUtil(om, propertie.getSign(), redisUtil);
                default -> throw new IllegalStateException("Unexpected value: " + algorithm);
            };
        }

        @Bean
        SignInterceptor signInterceptor(InterceptorsAutoConfigurationProperties properties, ObjectMapper om, RedisUtil redisUtil) {
            return new SignInterceptor(properties, signUtil(properties, om, redisUtil));
        }

        @Bean
        InterceptorsRegistrar interceptorsRegistrar(InterceptorsAutoConfigurationProperties properties, ObjectMapper om, RedisUtil redisUtil) {
            return new InterceptorsRegistrar(properties, signInterceptor(properties, om, redisUtil));
        }

        @Bean
        CacheFilter cacheFilter() {
            return new CacheFilter();
        }
    }

}
