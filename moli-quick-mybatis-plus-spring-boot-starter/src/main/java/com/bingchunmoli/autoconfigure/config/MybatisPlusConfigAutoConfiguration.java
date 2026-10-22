package com.bingchunmoli.autoconfigure.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author BingChunMoLi
 */
@AutoConfiguration(before = MybatisPlusAutoConfiguration.class,
        beforeName = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusInnerInterceptorAutoConfiguration")
@ConditionalOnClass(MybatisPlusInterceptor.class)
@ConditionalOnProperty(prefix = "moli.mybatis-plus", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MybatisPlusProperties.class)
public class MybatisPlusConfigAutoConfiguration {

    @Bean
    @ConditionalOnClass(PaginationInnerInterceptor.class)
    @ConditionalOnMissingBean(PaginationInnerInterceptor.class)
    public PaginationInnerInterceptor getPaginationInnerInterceptor(MybatisPlusProperties properties) {
        return new PaginationInnerInterceptor(properties.getDbType());
    }

    @Bean
    @ConditionalOnClass(MybatisPlusInterceptor.class)
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor mybatisPlusInterceptor(PaginationInnerInterceptor pagination) {
        MybatisPlusInterceptor mybatisPlusInterceptor = new MybatisPlusInterceptor();
        mybatisPlusInterceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        mybatisPlusInterceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        mybatisPlusInterceptor.addInnerInterceptor(pagination);
        return mybatisPlusInterceptor;
    }

    @Bean
    @ConditionalOnClass(MetaObjectHandler.class)
    @ConditionalOnMissingBean(MetaObjectHandler.class)
    public CommonMetaHandler commonMetaHandler() {
        return new CommonMetaHandler();
    }
}
