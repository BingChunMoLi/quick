package com.bingchunmoli.autoconfigure.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

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
    @ConditionalOnMissingBean(OptimisticLockerInnerInterceptor.class)
    public OptimisticLockerInnerInterceptor optimisticLockerInnerInterceptor() {
        return new OptimisticLockerInnerInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(BlockAttackInnerInterceptor.class)
    public BlockAttackInnerInterceptor blockAttackInnerInterceptor() {
        return new BlockAttackInnerInterceptor();
    }

    @Bean
    @ConditionalOnClass(MybatisPlusInterceptor.class)
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor mybatisPlusInterceptor(PaginationInnerInterceptor pagination,
                                                        List<InnerInterceptor> interceptors) {
        MybatisPlusInterceptor mybatisPlusInterceptor = new MybatisPlusInterceptor();
        // Spring orders the injected list; tenant/data-permission SQL must also affect pagination counts.
        interceptors.stream().filter(interceptor -> !(interceptor instanceof PaginationInnerInterceptor))
                .forEach(mybatisPlusInterceptor::addInnerInterceptor);
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
