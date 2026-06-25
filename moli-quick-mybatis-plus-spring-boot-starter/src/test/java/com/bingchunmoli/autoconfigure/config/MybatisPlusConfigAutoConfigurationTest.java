package com.bingchunmoli.autoconfigure.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusConfigAutoConfigurationTest {

    private final MybatisPlusConfigAutoConfiguration configuration = new MybatisPlusConfigAutoConfiguration();

    @Test
    void getPaginationInnerInterceptorShouldUseMysql() {
        PaginationInnerInterceptor interceptor = configuration.getPaginationInnerInterceptor();

        assertThat(interceptor.getDbType()).isEqualTo(DbType.MYSQL);
    }

    @Test
    void mybatisPlusInterceptorShouldContainExpectedInnerInterceptors() {
        MybatisPlusInterceptor interceptor = configuration.mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors())
                .hasSize(3)
                .anySatisfy(inner -> assertThat(inner).isInstanceOf(OptimisticLockerInnerInterceptor.class))
                .anySatisfy(inner -> assertThat(inner).isInstanceOf(BlockAttackInnerInterceptor.class))
                .anySatisfy(inner -> assertThat(inner).isInstanceOf(PaginationInnerInterceptor.class));
    }

    @Test
    void commonMetaHandlerShouldCreateHandler() {
        MetaObjectHandler handler = configuration.commonMetaHandler();

        assertThat(handler).isInstanceOf(CommonMetaHandler.class);
    }
}
