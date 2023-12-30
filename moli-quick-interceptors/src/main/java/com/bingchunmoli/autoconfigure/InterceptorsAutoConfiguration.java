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

/**
 * 自动配置拦截器
 *
 * @author MoLi
 */
@RequiredArgsConstructor
@EnableConfigurationProperties(InterceptorsAutoConfigurationProperties.class)
public class InterceptorsAutoConfiguration {

    /**
     * 自动配置签名
     * @author MoLi
     */
    @RequiredArgsConstructor
    @AutoConfiguration(after = {ObjectMapper.class, RedisUtil.class, InterceptorsAutoConfigurationProperties.class})
    @ConditionalOnProperty(prefix = "moli.interceptor.sign.sign", value = "enable", havingValue = "true")
    static class SignAutoConfiguration {

        /**
         * 注入signUtil
         * @param properties 配置properties
         * @param om json序列化
         * @param redisUtil redisUtil
         * @return signUtil
         */
        @Bean
        SignUtil signUtil(InterceptorsAutoConfigurationProperties properties, ObjectMapper om, RedisUtil redisUtil) {
            String algorithm = properties.getSign().getAlgorithm();
            return switch (algorithm) {
                case "SHA256WithRSA" -> new SHA256WithRSASignUtil(om, properties.getSign(), redisUtil);
                default -> throw new IllegalStateException("Unexpected value: " + algorithm);
            };
        }

        /**
         * 注入 signInterceptor
         * @param properties 配置properties
         * @param om json序列化
         * @param redisUtil redisUtil
         * @return signInterceptor
         */
        @Bean
        SignInterceptor signInterceptor(InterceptorsAutoConfigurationProperties properties, ObjectMapper om, RedisUtil redisUtil) {
            return new SignInterceptor(properties, signUtil(properties, om, redisUtil));
        }

        /**
         * 注入interceptorsRegistrar
         * @param properties 配置properties
         * @param om json序列化
         * @param redisUtil redisUtil
         * @return interceptorsRegistrar
         */
        @Bean
        InterceptorsRegistrar interceptorsRegistrar(InterceptorsAutoConfigurationProperties properties, ObjectMapper om, RedisUtil redisUtil) {
            return new InterceptorsRegistrar(properties, signInterceptor(properties, om, redisUtil));
        }

        /**
         * 注入CacheFilter
         * @return CacheFilter
         */
        @Bean
        CacheFilter cacheFilter() {
            return new CacheFilter();
        }
    }

}
