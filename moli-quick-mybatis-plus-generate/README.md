# moli-quick-mybatis-plus-generate

保留的 MyBatis-Plus FastAutoGenerator 包装模块。需要宿主显式调用生成方法，不会在启动时自动写文件。

[项目总览](../README.md)

当前源码：Java 17，Spring Boot 依赖管理 4.1.0，Quick 版本 `1.0.13-bate`。依赖示例使用项目总览中的统一版本管理；工具和聚合模块的特殊接入方式见下文。

## 引入

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-mybatis-plus-generate</artifactId>
  <version>1.0.13-bate</version>
</dependency>
```

当前独立 Quick BOM 未列出此旧模块，请显式指定版本；根构建父 POM 中另有版本管理。新项目需要 UI、CLI、原生 MyBatis 或 YAML 模板时使用 [统一生成器](../moli-quick-codegen/README.md)。

## 配置

```yaml
moli:
  gen:
    base-package: com.example.demo
    author: developer
    output-dir: ./generated/src/main/java
    comment-date: yyyy-MM-dd
    swagger-version: V3
    version: 1.0.0
```

配置类型为 `GeneratorConfigProperties`，Swagger 枚举见 [SwaggerEnums](src/main/java/com/bingchunmoli/generate/enums/SwaggerEnums.java)。此模块使用宿主 `DataSource`，应在开发工具或测试环境中执行。

## 显式执行

```java
import com.bingchunmoli.generate.GenerateAutoConfiguration;
import com.bingchunmoli.generate.config.GeneratorConfigProperties;

// dataSource 为宿主已有数据源；先设置配置，检查输出目录
var properties = new GeneratorConfigProperties();
properties.setBasePackage("com.example.demo");
properties.setOutputDir("./generated/src/main/java");
properties.setAuthor("developer");
var generator = new GenerateAutoConfiguration().buildGenerate(dataSource, properties);
generator.strategyConfig(strategy -> strategy.addInclude("sys_user"));
generator.execute();
```

Spring 集成通过 `GenerateAutoConfiguration` 使用同一方法，自动配置条件要求 `DataSourceProperties` Bean。生成实体启用 ActiveRecord、Lombok，使用下划线转驼峰；默认启用 Springdoc 注解。当前实现显式选择 Velocity 引擎，不能把资源目录中的 FreeMarker 模板当作已启用功能。`active-record` 属性尚未控制实际策略。

## 边界

本模块不提供统一 YAML、网页预览或安全输出事务，也不沿用新生成器的“默认不覆盖”契约。执行前按 FastAutoGenerator 的实际策略检查表选择、输出路径、模板及覆盖行为。此次文档补充不改变旧 API。
