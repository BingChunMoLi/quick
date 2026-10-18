# Quick 统一代码生成器

[项目总览](../README.md)

当前源码版本 `1.0.13-bate`，Java 17 / Spring Boot 依赖管理 4.1.0；下方省略版本的依赖示例需先导入项目总览中的 Quick BOM。


通过同一套 JDBC 元数据和 FreeMarker 模板，生成原生 MyBatis 或 MyBatis-Plus 后端代码。支持 MySQL、PostgreSQL；浏览器、CLI、Maven 插件共享 YAML 配置。

## 构建和独立运行

需要 JDK 17+、Maven 3.9+。Maven 默认下载固定版本 Node/npm，使用前端锁文件构建 UI，并将页面打入 Starter。

```shell
mvn -f moli-quick-codegen/pom.xml package
java -jar moli-quick-codegen/moli-quick-codegen-app/target/moli-quick-codegen-app-1.0.13-bate-exec.jar
```

打开 <http://127.0.0.1:8088/moli-codegen/>。通过数据源、表选择、字段配置、模板编辑、预览五个页面完成生成，下载 ZIP 后解压到项目。

UI 不保存数据库密码到浏览器存储，不创建生成器业务表，也不写入服务器项目目录。导出 YAML 默认清除明文密码，包括 JDBC URL 的密码参数和 userinfo 密码；`${DB_PASSWORD}` 这类环境变量引用保留。UI 编辑的模板以 `content` 内联形式导出，可直接交给 CLI/Maven 使用。

仅验证后端、跳过前端构建时使用 `-Dcodegen.ui.skip=true`；这不适用于需要内置页面的干净发布构建。前端单独开发：

```shell
cd moli-quick-codegen/moli-quick-codegen-spring-boot-starter/src/main/frontend
npm ci
npm run dev
```

## 嵌入 Spring Boot 项目

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-codegen-spring-boot-starter</artifactId>
  <version>1.0.13-bate</version>
</dependency>
```

```yaml
moli:
  codegen:
    enabled: true
```

Starter 跟随当前仓库的 Spring Boot 4 依赖管理。默认关闭；关闭时不注册生成 API、UI 资源或生成器 Bean。启用后访问 `/moli-codegen/`。已有 Spring Security 规则继续生效，不自动放行路径；页面和 API 应交给宿主的开发者访问控制管理。生成结果本身使用通用 Spring API，覆盖 Spring Boot 3 和 4 的源码编译验证。

嵌入模式可以通过 `datasource.beanName: businessDataSource` 复用宿主具名 `DataSource`，此时 JDBC URL 和用户名密码不用于连接。页面会列出可用 Bean 名称。具名数据源只适用于 Web 模式，CLI/Maven 必须提供 JDBC 配置。

## CLI

```shell
java -jar moli-quick-codegen/moli-quick-codegen-cli/target/moli-quick-codegen-cli-1.0.13-bate-exec.jar tables --config codegen.yml
java -jar moli-quick-codegen/moli-quick-codegen-cli/target/moli-quick-codegen-cli-1.0.13-bate-exec.jar preview --config codegen.yml
java -jar moli-quick-codegen/moli-quick-codegen-cli/target/moli-quick-codegen-cli-1.0.13-bate-exec.jar generate --config codegen.yml
java -jar moli-quick-codegen/moli-quick-codegen-cli/target/moli-quick-codegen-cli-1.0.13-bate-exec.jar generate --config codegen.yml --zip generated.zip
```

所有模板文件和输出目录相对 YAML 所在目录解析；`--output` 可替换 `output.directory`。默认遇到已有文件就失败，`--overwrite` 或 `output.overwrite: true` 才允许覆盖。`--zip` 和 `--output` 不能同时指定。失败退出码为 1，命令参数错误为 2。

## Maven 插件

本地开发先执行 `mvn -N install -Dgpg.skip=true` 安装根父 POM，再执行 `mvn -f moli-quick-codegen/pom.xml install -Dgpg.skip=true`。也可从已发布仓库引用插件：

```xml
<plugin>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-codegen-maven-plugin</artifactId>
  <version>1.0.13-bate</version>
  <configuration><config>codegen.yml</config></configuration>
</plugin>
```

```shell
mvn -N com.bingchunmoli:moli-quick-codegen-maven-plugin:1.0.13-bate:tables -Dcodegen.config=codegen.yml
mvn -N com.bingchunmoli:moli-quick-codegen-maven-plugin:1.0.13-bate:preview -Dcodegen.config=codegen.yml
mvn -N com.bingchunmoli:moli-quick-codegen-maven-plugin:1.0.13-bate:generate -Dcodegen.config=codegen.yml
```

插件配置文件相对当前 Maven 模块解析，文件内路径仍相对配置文件解析。支持 `codegen.output`、`codegen.zip`、`codegen.overwrite`；不自动绑定构建生命周期。PowerShell 中包含点的 `-D` 参数请整体加引号，例如 `'-Dcodegen.config=codegen.yml'`。

## 配置与生成约定

参考 [MySQL 示例](examples/mysql.yml) 和 [PostgreSQL 示例](examples/postgresql.yml)。

| 配置 | 默认值 / 用途 |
|---|---|
| `schemaVersion` | `1`，不接受其他版本 |
| `datasource` | `url`、`username`、`password`，可选 `catalog`、`schema`、`beanName` |
| `orm` | `mybatis` 或 `mybatis-plus`，默认 `mybatis` |
| `basePackage`、`author`、`tablePrefix` | 基础包、注释作者、移除的表名前缀 |
| `responseStyle` | `generic` 默认；`quick` 使用 `ResultVO` |
| `lombok`、`openApi` | 默认关闭，启用后需业务项目提供对应依赖 |
| `tables` | 表对象列表，包含 `name`、可选 `className`、`columns` |
| `artifacts` | 默认生成 entity/controller/service/serviceImpl/mapper/xml |
| `output.directory` | CLI/Maven 输出根目录，默认当前配置目录 |
| `output.javaDirectory`、`output.xmlDirectory` | 默认 `src/main/java`、`src/main/resources/mapper` |
| `output.directories` | 按 artifact 覆盖目录，例如 `controller: web/src/main/java` |
| `templates`、`variables` | 模板覆盖、额外模板、自定义 JSON/YAML 数据 |

字段覆盖键为数据库原始列名。支持 `property`、`javaType`、`jdbcType`、`typeHandler`、`insert`、`update`、`query`、`queryType`。Java 类型使用 `String`、`Long` 等包装类型或完整类名；JDBC 类型使用 `VARCHAR`、`OTHER` 等名称。`queryType` 支持 `EQ` 和字符串 `LIKE`。

原生 MyBatis 选择生成 XML 时，会在渲染前校验 Java 属性是否为 OGNL 保留字，例如 `or`、
`eq`、`not`、`and`、`in`。错误信息包含表名、列名及属性名；请在 UI 字段配置或 YAML 中
通过 `columns.<数据库列名>.property` 改名，例如 `columns: {or: {property: orValue}}`。
数据库列名保持原样引用；显式属性覆盖同样接受校验，仅生成 Entity 时不受此项限制。

- MySQL 使用 catalog，PostgreSQL 使用 schema，未指定时取 JDBC 连接默认值；数据库标识符按对应方言引用，schema 中的 `_`、`%` 按字面匹配。PostgreSQL 无当前 schema 时必须显式配置，遇到同名表的歧义则报错。
- 完整 CRUD 要求单列主键；无主键和联合主键可选仅生成 Entity。单列主键参数按实际类型生成，不固定为 Long。
- 数据库自增列和生成列不参与普通插入/更新，主键不参与更新。更新仅写入非空可更新字段；没有任何待更新字段时返回 false。首版不提供将字段显式更新为 NULL 的 PATCH 语义。
- 默认查询只应用非空条件；列表按主键排序，页码从 1 开始，单页大小 1–1000。
- 单独选择部分文件时，其他被引用类型应由业务项目已有代码提供。额外模板不会自动补齐业务依赖。
- 常规数值、字符串、二进制、金额、日期时间、PostgreSQL UUID 有默认映射。JSON、数组等复杂类型必须明确提供 Java 类型和 TypeHandler。
- Java/XML 渲染全部成功、路径和冲突检查完成后才开始写入。默认不覆盖文件，不进行代码合并；底层文件系统异常导致批量移动中断时，可能已有部分文件写入。

### 生成代码接入

原生 MyBatis 使用业务项目对应 Boot 版本的 MyBatis Starter，配置 mapper XML 扫描路径；默认 `classpath*:mapper/**/*.xml`。

MyBatis-Plus 模板以 **3.5.17** 为基线，该版本的 Service 类型位于 `com.baomidou.mybatisplus.spring.service`。Boot 3 使用 `mybatis-plus-spring-boot3-starter`，Boot 4 使用 `mybatis-plus-spring-boot4-starter`，并引入同版本 `mybatis-plus-jsqlparser`，注册分页拦截器：

```java
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
    return interceptor;
}
```

上述类型位于 `com.baomidou.mybatisplus.extension.plugins` 和 `com.baomidou.mybatisplus.extension.plugins.inner`。已有 MyBatis-Plus 拦截器时，将分页插件加入现有 Bean。

UUID 字段会额外生成一次 `model/UuidTypeHandler.java`。使用 UUID 主键时，将生成的 `model` 包加入 `mybatis.type-handlers-package` 或 `mybatis-plus.type-handlers-package`；XML 中的字段映射也会引用该 Handler。通用分页响应 `model/PageResult.java` 每个生成批次只输出一次。

`quick` 响应风格需要 `moli-common-beans`；默认通用风格不依赖 quick 或若依公共业务类。

## 模板扩展

模板项使用 `content` 或 `file` 二选一，`outputPath` 可替换默认路径。新增模板必须提供输出路径和模板内容，例如 PostgreSQL 示例中的 DTO。Web 模式只允许内联内容；本地 CLI/Maven 支持读取外部模板文件。

| 上下文变量 | 含义 |
|---|---|
| `basePackage`、`packagePath` | Java 包名、对应目录 |
| `className`、`entityVariable`、`route` | 实体名、变量名、REST 路径 |
| `entityType` | 实体的完整 Java 类名；内置模板用它避免与 List、PageResult 等类型重名 |
| `author`、`comment`、`tableName` | 注释和表名 |
| `qualifiedTable`、`dialect`、`orm` | 引用后的全限定表名、数据库、ORM 风格 |
| `columns` | 字段列表 |
| `insertColumns`、`updateColumns`、`queryColumns` | 按生成策略筛选的字段列表 |
| `pk`、`singleKey` | 主键字段上下文、是否单列主键 |
| `lombok`、`openApi`、`quick` | 生成选项 |
| `variables` | 用户自定义数据 |

字段包含 `name`、`property`、`javaType`、`jdbcType`、`typeHandler`、`comment`、`getter`、`setter`、`sql`、`pk`、`auto`、`generated`、`nullable`、`insert`、`update`、`query`、`queryType`，以及 SQL 参数绑定字符串 `bind`、`filterBind`、`keyBind`。

模板上下文不提供数据源或连接对象，也不提供配置密码；禁用 Java 类实例化及 `?api`。公共分页/UUID 模板每批执行一次，其表上下文取配置中的第一张表，公共模板应避免依赖具体实体名称。

Java 扩展可以使用 `CodeGenerator(MetadataReader, TemplateRenderer)` 替换读取/渲染实现，或直接调用接受 `TableMetadata` 和 `DatabaseDialect` 的 preview 重载。`ConnectionProvider` 可对接现有连接管理，`DatabaseDialect` 提供标识符引用和表名限定接口。

## API

所有接口位于 `/moli-codegen/api`。POST JSON 使用同一 `GenerationConfig`：

| 方法 / 路径 | 返回 |
|---|---|
| GET `/datasources` | 可用宿主 DataSource 名称 |
| POST `/connection` | 连接到的数据库类型 |
| POST `/tables` | 表列表 |
| POST `/columns` | 配置所选表的字段元数据 |
| GET `/templates?orm=mybatis` | 内置模板源码 |
| POST `/preview` | `{files:[{relativePath,content}],diagnostics:[]}` |
| POST `/export` | ZIP 字节流 |
| POST `/config/import` | text/plain YAML → 配置对象 |
| POST `/config/export` | 配置对象 → 脱敏 YAML |

可定位的配置/模板错误返回 HTTP 400 和 `diagnostics`；连接错误只包含 SQLState，不回传驱动异常、JDBC URL 或密码。

## 验证

```shell
mvn -f moli-quick-codegen/pom.xml test -Dcodegen.ui.skip=true
mvn -f moli-quick-codegen/moli-quick-codegen-core/pom.xml test -Pgenerated-boot3
```

单元测试覆盖生成源码编译、XML 加载、UUID、自定义模板、YAML 往返、文件冲突、ZIP 稳定性、路径越界、命令错误及 Web 开关/API。额外验证实体与框架类型重名、真实 Lombok 注解处理、自定义访问器、schema 通配符转义、JDBC URL 密码脱敏和 UI 原型属性字段名。Windows 链接测试优先使用符号链接，权限不足时改用 Junction；两种均不可用才跳过。

真实数据库集成测试需要专用测试库以及建表/删表权限；PostgreSQL 还需要创建/删除 schema 的权限，以验证元数据匹配的隔离性。测试使用唯一表/schema 名，并在 finally 中清理自身创建的对象，不使用 H2 替代。

设置 `CODEGEN_TEST_MYSQL_URL`、`CODEGEN_TEST_MYSQL_USERNAME`、`CODEGEN_TEST_MYSQL_PASSWORD` 和 `CODEGEN_TEST_POSTGRESQL_URL`、`CODEGEN_TEST_POSTGRESQL_USERNAME`、`CODEGEN_TEST_POSTGRESQL_PASSWORD`，可选 `CODEGEN_TEST_POSTGRESQL_SCHEMA`，执行：

```shell
mvn -f moli-quick-codegen/moli-quick-codegen-core/pom.xml verify -Pdatabase-it -Dgpg.skip=true
```

集成测试对两种数据库和两种 ORM 验证真实元数据、自增主键回填、金额、中文 LIKE、数据库生成列、分页总数、空更新及 CRUD。省略测试数据库 URL 时显式失败，不静默跳过。

## 子模块 README 导航

| 模块 | 用途 |
| --- | --- |
| [moli-quick-codegen-core](moli-quick-codegen-core/README.md) | 非 Spring 内核、JDBC 元数据、FreeMarker 模板 |
| [moli-quick-codegen-cli](moli-quick-codegen-cli/README.md) | 可执行 JAR，tables/preview/generate |
| [moli-quick-codegen-maven-plugin](moli-quick-codegen-maven-plugin/README.md) | Maven goals，手动生成到项目 |
| [moli-quick-codegen-spring-boot-starter](moli-quick-codegen-spring-boot-starter/README.md) | 嵌入 API/UI，默认关闭 |
| [moli-quick-codegen-app](moli-quick-codegen-app/README.md) | 本机独立 UI，127.0.0.1:8088 |
