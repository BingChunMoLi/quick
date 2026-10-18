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

宿主还需提供 MyBatis-Plus 启动集成、数据源和数据库驱动；映射接口继续使用原生 `@Mapper`/`@MapperScan`。当前 POM 的 `mybatis-plus-spring-boot3-starter` 与 MySQL 驱动均为 optional，不会作为完整启动依赖传递给宿主。源码整体采用 Boot 4.1 依赖管理，宿主需要核对实际 MyBatis-Plus/Boot 运行时组合。

## 默认行为

| Bean | 行为 |
| --- | --- |
| `MybatisPlusInterceptor` | 依次加入乐观锁、全表更新/删除阻断、分页拦截器 |
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

PostgreSQL 等场景应替换整个拦截器组合：

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

上例类型均来自 `com.baomidou.mybatisplus`。提供 `MetaObjectHandler` Bean 可替换默认填充逻辑。不要假定仅替换分页 Bean 就能改变默认组合内部创建的分页实例。

本模块无 `moli.*` 开关。代码生成请使用 [统一代码生成器](../moli-quick-codegen/README.md)，运行时插件与生成工具分别接入。
