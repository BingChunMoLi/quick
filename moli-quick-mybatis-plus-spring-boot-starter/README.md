# moli-quick-mybatis-plus-spring-boot-starter

为 MyBatis-Plus 提供拦截器组合和时间字段填充。

[项目总览](../README.md)

当前源码：Java 17，Spring Boot 依赖管理 4.1.0，Quick 版本 `1.0.13-bate`。依赖示例使用项目总览中的统一版本管理；工具和聚合模块的特殊接入方式见下文。

## 引入依赖

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-mybatis-plus-spring-boot-starter</artifactId>
</dependency>
```

本 Starter 传递引入 MyBatis-Plus Boot 4 Starter，宿主只需提供数据源配置和数据库驱动，映射接口继续使用原生 `@Mapper`/`@MapperScan`。MySQL 驱动仍为 optional，其他数据库使用相应驱动；不要额外引入 Boot 3 的 MyBatis-Plus Starter。

## 默认行为

| Bean | 行为 |
| --- | --- |
| `MybatisPlusInterceptor` | 按 Spring 顺序合并宿主与默认插件，分页放在最后 |
| `PaginationInnerInterceptor` | 默认 `DbType.MYSQL` |
| `CommonMetaHandler` | 插入填充 `createTime`；更新填充 `updateTime`，类型为 `LocalDateTime` |

实体对应字段需要启用填充策略，例如：

```java
@TableField(fill = FieldFill.INSERT)
private LocalDateTime createTime;

@TableField(fill = FieldFill.UPDATE)
private LocalDateTime updateTime;
```

`@TableField`、`FieldFill` 位于 `com.baomidou.mybatisplus.annotation`；乐观锁字段还需按 MyBatis-Plus 规则声明 `@Version`。

## 其他数据库与扩展

PostgreSQL 等场景可直接配置方言，或提供自己的 PaginationInnerInterceptor Bean，默认组合会使用宿主实例：

```yaml
moli:
  mybatis-plus:
    enabled: true
    db-type: POSTGRE_SQL
```

声明 `TenantLineInnerInterceptor`、数据权限等 `InnerInterceptor` Bean 即可加入默认组合，无需重新声明整个 MybatisPlusInterceptor。宿主插件的 `Ordered`/`@Order` 顺序会保留，分页在其后执行，使分页查询与总数统计应用相同的租户、权限条件。乐观锁和全表操作保护也会使用宿主提供的同类型 Bean。分页应配置单个实例；多个实例时需要通过 `@Primary` 明确选择。

需要完全接管插件组合时，可以提供：

```java
@Bean
MybatisPlusInterceptor mybatisPlusInterceptor() {
    var interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
    interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
    return interceptor;
}
```

上例类型均来自 `com.baomidou.mybatisplus`。提供 `MetaObjectHandler` Bean 可替换默认填充逻辑。

`moli.mybatis-plus.enabled=false` 关闭 Quick 的分页、插件组合及字段填充 Bean，不关闭原生 MyBatis-Plus 数据源和 Mapper 集成。代码生成请使用 [统一代码生成器](../moli-quick-codegen/README.md)，运行时插件与生成工具分别接入。
