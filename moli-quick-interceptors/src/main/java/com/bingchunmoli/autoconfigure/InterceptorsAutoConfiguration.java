package com.bingchunmoli.autoconfigure;

import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import com.bingchunmoli.filter.CacheFilter;
import com.bingchunmoli.interceptor.SignInterceptor;
import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import com.bingchunmoli.registrar.InterceptorsRegistrar;
import com.bingchunmoli.util.MD5SignUtil;
import com.bingchunmoli.util.MD5WithRSASignUtil;
import com.bingchunmoli.util.SHA256WithRSASignUtil;
import com.bingchunmoli.util.SignUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 自动配置拦截器
 *
 * @author MoLi
 */
@AutoConfiguration
@Import(InterceptorsAutoConfiguration.SignAutoConfiguration.class)
@EnableConfigurationProperties(InterceptorsAutoConfigurationProperties.class)
public class InterceptorsAutoConfiguration {

    /**
     * 自动配置签名
     * @author MoLi
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "moli.interceptor.sign.sign", value = "enable", havingValue = "true")
    static class SignAutoConfiguration {

        /**
         * 注入signUtil
         * @param propertie 配置properties
         * @param mappers 宿主提供的 Jackson 2 Mapper；缺失时使用模块内部 Mapper
         * @param redisUtil redisUtil
         * @return signUtil
         */
        @Bean
        @ConditionalOnMissingBean(SignUtil.class)
        SignUtil signUtil(InterceptorsAutoConfigurationProperties propertie, ObjectProvider<ObjectMapper> mappers,
                          RedisUtil redisUtil) {
            ObjectMapper om = mappers.getIfAvailable(() -> new ObjectMapper().findAndRegisterModules());
            String algorithm = propertie.getSign().getAlgorithm();
            return switch (algorithm) {
                case "SHA256WithRSA" -> new SHA256WithRSASignUtil(om, propertie.getSign(), redisUtil);
                case "MD5WithRSA" -> new MD5WithRSASignUtil(om, propertie.getSign(), redisUtil);
                case "MD5" -> new MD5SignUtil(om, propertie.getSign(), redisUtil);
                default -> throw new IllegalStateException("Unexpected value: " + algorithm);
            };
        }

        /**
         * 注入 signInterceptor
         * @param properties 配置properties
         * @param signUtil 选定的验签实现
         * @return signInterceptor
         */
        @Bean
        @ConditionalOnMissingBean(SignInterceptor.class)
        SignInterceptor signInterceptor(InterceptorsAutoConfigurationProperties properties, SignUtil signUtil) {
            return new SignInterceptor(properties, signUtil);
        }

        /**
         * 注入interceptorsRegistrar
         * @param properties 配置properties
         * @param signInterceptor 选定的拦截器
         * @return interceptorsRegistrar
         */
        @Bean
        @ConditionalOnMissingBean(InterceptorsRegistrar.class)
        InterceptorsRegistrar interceptorsRegistrar(InterceptorsAutoConfigurationProperties properties, SignInterceptor signInterceptor) {
            return new InterceptorsRegistrar(properties, signInterceptor);
        }

        /**
         * 注入CacheFilter
         * @return CacheFilter
         */
        @Bean
        @ConditionalOnMissingBean(CacheFilter.class)
        CacheFilter cacheFilter() {
            return new CacheFilter();
        }
    }

}
