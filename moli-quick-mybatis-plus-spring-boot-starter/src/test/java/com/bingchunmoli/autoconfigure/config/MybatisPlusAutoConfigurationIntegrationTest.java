package com.bingchunmoli.autoconfigure.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusAutoConfigurationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MybatisPlusConfigAutoConfiguration.class));

    @Test
    void shouldAutoConfigureExpectedMybatisPlusInfrastructureForBoot406() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PaginationInnerInterceptor.class);
            assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
            assertThat(context).hasSingleBean(MetaObjectHandler.class);

            assertThat(context.getBean(PaginationInnerInterceptor.class).getDbType()).isEqualTo(DbType.MYSQL);
            assertThat(context.getBean(MybatisPlusInterceptor.class).getInterceptors())
                    .hasSize(3)
                    .anySatisfy(inner -> assertThat(inner).isInstanceOf(OptimisticLockerInnerInterceptor.class))
                    .anySatisfy(inner -> assertThat(inner).isInstanceOf(BlockAttackInnerInterceptor.class))
                    .anySatisfy(inner -> assertThat(inner).isInstanceOf(PaginationInnerInterceptor.class));
            assertThat(context.getBean(MetaObjectHandler.class)).isInstanceOf(CommonMetaHandler.class);
        });
    }

    @Test
    void shouldBackOffWhenConsumerProvidesCustomBeans() {
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        MybatisPlusInterceptor mybatisPlusInterceptor = new MybatisPlusInterceptor();
        MetaObjectHandler metaObjectHandler = new CommonMetaHandler();

        contextRunner
                .withBean(PaginationInnerInterceptor.class, () -> paginationInnerInterceptor)
                .withBean(MybatisPlusInterceptor.class, () -> mybatisPlusInterceptor)
                .withBean(MetaObjectHandler.class, () -> metaObjectHandler)
                .run(context -> {
                    assertThat(context.getBean(PaginationInnerInterceptor.class)).isSameAs(paginationInnerInterceptor);
                    assertThat(context.getBean(MybatisPlusInterceptor.class)).isSameAs(mybatisPlusInterceptor);
                    assertThat(context.getBean(MetaObjectHandler.class)).isSameAs(metaObjectHandler);
                });
    }
}
