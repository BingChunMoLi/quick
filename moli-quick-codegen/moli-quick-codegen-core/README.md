# moli-quick-codegen-core

不依赖 Spring 容器的统一代码生成内核：配置读取、JDBC 元数据、方言、FreeMarker 渲染和文件输出。

[项目总览](../../README.md) · [所属模块](../README.md)

依赖版本通过项目总览中的 Quick BOM 对齐为当前源码版本 `1.0.13-bate`，Java 17 / Spring Boot 4.1.0。以下示例需与功能族的完整接入说明配合使用。

## 引入

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-codegen-core</artifactId>
</dependency>
```

## 最小调用

```java
import com.bingchunmoli.codegen.*;
import com.bingchunmoli.codegen.jdbc.JdbcConnections;
import java.nio.file.Files;
import java.nio.file.Path;

Path yaml = Path.of("codegen.yml").toAbsolutePath();
var config = new ConfigCodec().read(yaml);
var generator = new CodeGenerator();
var files = generator.preview(config, yaml.getParent(), JdbcConnections.of(config.datasource));
Files.write(Path.of("generated.zip"), FileOutputs.zip(files));
// 确认结果后写入目录；false 表示遇到已有文件就失败
FileOutputs.write(files, Path.of("generated-project"), false);
```

YAML 完整字段及模板上下文见 [生成器指南](../README.md)，可使用 [MySQL 示例](../examples/mysql.yml) 或 [PostgreSQL 示例](../examples/postgresql.yml)。

## 公共接口

| 类型 | 用途 |
| --- | --- |
| `GenerationConfig` | schemaVersion=1 的统一配置 |
| `TableMetadata/ColumnMetadata` | 表、字段、主键、类型等元数据 |
| `GeneratedFile` | 相对路径及文本内容 |
| `CodeGenerator.tables/columns/preview/templates` | 读取及渲染 |
| `ConfigCodec.read/write` | YAML 读取与导出 |
| `FileOutputs.zip/write` | ZIP 和目录输出 |
| `ConnectionProvider/DatabaseDialect` | 连接与方言扩展点 |

`preview` 不写目标文件。模板文件及输出设置以配置目录解析，只有目录输出显式允许 overwrite 才替换文件；不做代码合并。

## 支持与限制

支持 MySQL catalog、PostgreSQL schema，MyBatis/MyBatis-Plus 两套模板。完整 CRUD 要求单列主键；无/联合主键可只生成实体。未知类型需显式映射，原生 MyBatis 属性名需要避开 OGNL 保留字。具名 DataSource 仅由 Web 入口解析；直接使用内核可提供自己的 ConnectionProvider。

模板可读取元数据与变量，不应读取数据库凭证。验证和渲染先于写入，但底层文件系统失败不保证多文件事务回滚。真实数据库与生成代码验证 profile 见上级 README。
