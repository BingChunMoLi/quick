package com.bingchunmoli.autoconfigure.config;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import com.baomidou.mybatisplus.annotation.DbType;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusBoot4IntegrationTest {
    @Test
    void hostTenantPluginFiltersRowsAndPaginationCounts() {
        var tenant = new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() { return new LongValue(7); }
        });
        new ApplicationContextRunner().withUserConfiguration(Host.class)
                .withBean(TenantLineInnerInterceptor.class, () -> tenant)
                .withPropertyValues("spring.datasource.url=jdbc:h2:mem:quick_tenant", "spring.datasource.username=sa")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(MybatisPlusInterceptor.class).getInterceptors())
                            .contains(tenant).last().isInstanceOf(PaginationInnerInterceptor.class);
                    var factory = context.getBean(SqlSessionFactory.class);
                    factory.getConfiguration().addMapper(TenantMapper.class);
                    try (var connection = context.getBean(javax.sql.DataSource.class).getConnection();
                         var statement = connection.createStatement()) {
                        statement.execute("create table starter_tenant_items(id int primary key, tenant_id int)");
                        statement.execute("insert into starter_tenant_items values (1, 7), (2, 8)");
                    }
                    try (var session = factory.openSession()) {
                        assertThat(session.getMapper(TenantMapper.class).ids()).containsExactly(1);
                        Page<Integer> page = new Page<>(1, 10);
                        assertThat(session.getMapper(TenantMapper.class).page(page)).containsExactly(1);
                        assertThat(page.getTotal()).isEqualTo(1);
                    }
                });
    }

    @Test
    void hostPaginationDoesNotRemoveTheOtherQuickPlugins() {
        var pagination = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        new ApplicationContextRunner().withUserConfiguration(Host.class)
                .withBean(PaginationInnerInterceptor.class, () -> pagination)
                .withPropertyValues("spring.datasource.url=jdbc:h2:mem:quick_override", "spring.datasource.username=sa")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(MybatisPlusInterceptor.class);
                    assertThat(context.getBean(MybatisPlusInterceptor.class).getInterceptors())
                            .hasSize(3).contains(pagination)
                            .anyMatch(OptimisticLockerInnerInterceptor.class::isInstance)
                            .anyMatch(BlockAttackInnerInterceptor.class::isInstance);
                });
    }

    @Test
    void nativeBoot4HostDiscoversQuickPluginsAndExecutesPaginatedSql() {
        new ApplicationContextRunner().withUserConfiguration(Host.class)
                .withPropertyValues("spring.datasource.url=jdbc:h2:mem:quick_starter;MODE=MySQL;DB_CLOSE_DELAY=-1",
                        "spring.datasource.username=sa", "spring.datasource.password=")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(SqlSessionFactory.class);
                    var factory = context.getBean(SqlSessionFactory.class);
                    assertThat(factory.getConfiguration().getInterceptors())
                            .contains(context.getBean(MybatisPlusInterceptor.class));
                    factory.getConfiguration().addMapper(PagingMapper.class);
                    try (var connection = context.getBean(javax.sql.DataSource.class).getConnection();
                         var statement = connection.createStatement()) {
                        statement.execute("create table if not exists starter_items(id int primary key)");
                        statement.execute("delete from starter_items");
                        statement.execute("insert into starter_items values (1), (2), (3)");
                    }
                    try (var session = factory.openSession()) {
                        Page<Integer> page = new Page<>(1, 1);
                        assertThat(session.getMapper(PagingMapper.class).ids(page)).containsExactly(1);
                        assertThat(page.getTotal()).isEqualTo(3);
                    }
                });
    }

    public interface PagingMapper {
        @Select("select id from starter_items order by id")
        List<Integer> ids(IPage<Integer> page);
    }

    public interface TenantMapper {
        @Select("select id from starter_tenant_items order by id")
        List<Integer> ids();

        @Select("select id from starter_tenant_items order by id")
        List<Integer> page(IPage<Integer> page);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Host { }
}
