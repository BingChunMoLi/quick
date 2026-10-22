# Quick · Spring Boot 基础组件

Quick 是按需组合的 Maven 多模块项目，覆盖数据访问、缓存、Web 基础能力、请求签名、登录授权、对象存储和代码生成。根 artifact `moli-quick-spring-boot-starter` 是构建/依赖管理 POM，不是一次引入全部能力的运行时 Starter。

## 当前源码基线

| 项目 | 值 |
| --- | --- |
| groupId | `com.bingchunmoli` |
| Quick 版本 | `1.0.13-bate`（保留现有 bate 拼写） |
| Java | `17` 及以上，源码按 release 17 编译 |
| Spring Boot 依赖管理 | `4.1.0` |
| 构建 | Maven；生成器 UI 由 Maven 管理固定 Node/npm |

本文描述当前源码，不将历史 Spring Boot 3 接入说明当作本版本兼容承诺。代码生成器输出的业务代码另有 Boot 3/4 编译验证，见其专门文档。

## 快速接入

宿主先管理 Spring Boot 依赖（例如使用 Boot 4.1.0 parent），再导入 Quick BOM：

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.bingchunmoli</groupId>
      <artifactId>moli-quick-dependencies</artifactId>
      <version>1.0.13-bate</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

只添加业务真正需要的组件，例如：

```xml
<dependencies>
  <dependency>
    <groupId>com.bingchunmoli</groupId>
    <artifactId>moli-quick-web-spring-boot-starter</artifactId>
  </dependency>
</dependencies>
```

示例版本对应源码；所用仓库尚无对应制品时，先从本仓库本地安装。避免使用 `LATEST`，同一项目的 Quick 依赖保持同一版本。BOM 的直接管理范围与插件版本注意事项见 [依赖管理](moli-quick-dependencies/README.md)。

## 全部模块导航

当前 reactor 包含 47 个 Maven 项目：根构建 POM 加下列 46 个模块。每个模块目录均有 README，聚合模块的文档进一步说明子模块关系。

### 基础组件、运行时与构建工具

| 模块 | 用途 |
| --- | --- |
| [moli-quick-dependencies](moli-quick-dependencies/README.md) | 版本 BOM（pom import） |
| [moli-common-beans](moli-common-beans/README.md) | ResultVO、业务码、环境/时间工具 |
| [moli-quick-aop-boot-starter](moli-quick-aop-boot-starter/README.md) | 显式启用的方法日志与执行时间切面 |
| [moli-quick-redis-spring-boot-starter](moli-quick-redis-spring-boot-starter/README.md) | Redis 对象序列化及 RedisUtil |
| [moli-quick-mybatis-plus-spring-boot-starter](moli-quick-mybatis-plus-spring-boot-starter/README.md) | MyBatis-Plus 分页、乐观锁、全表操作阻断、时间填充 |
| [moli-quick-interceptors](moli-quick-interceptors/README.md) | MVC 请求签名、JSON 重复读取、nonce 防重放 |
| [moli-quick-security-util](moli-quick-security-util/README.md) | 用户上下文、Session 退出、JWT 工具与可选默认链 |
| [moli-quick-idempotency-spring-boot-starter](moli-quick-idempotency-spring-boot-starter/README.md) | 方法幂等，内存/Redis/自定义存储 |
| [moli-quick-rate-limit-spring-boot-starter](moli-quick-rate-limit-spring-boot-starter/README.md) | 固定窗口方法限流，内存/Redis/自定义存储 |
| [moli-quick-web-spring-boot-starter](moli-quick-web-spring-boot-starter/README.md) | 统一异常、参数校验错误、请求 ID/MDC |
| [moli-quick-mybatis-plus-generate](moli-quick-mybatis-plus-generate/README.md) | 旧 FastAutoGenerator 包装，显式手动调用 |
| [moli-quick-spring-boot-starter-common-test](moli-quick-spring-boot-starter-common-test/README.md) | 仓库演示/回归应用，非业务测试 Starter |

### 统一代码生成器

| 模块 | 用途 |
| --- | --- |
| [moli-quick-codegen](moli-quick-codegen/README.md) | 聚合父模块、完整配置与生成规则 |
| [moli-quick-codegen-core](moli-quick-codegen/moli-quick-codegen-core/README.md) | 非 Spring 内核、JDBC 元数据、FreeMarker 模板 |
| [moli-quick-codegen-cli](moli-quick-codegen/moli-quick-codegen-cli/README.md) | 可执行 JAR，tables/preview/generate |
| [moli-quick-codegen-maven-plugin](moli-quick-codegen/moli-quick-codegen-maven-plugin/README.md) | Maven goals，手动生成到项目 |
| [moli-quick-codegen-spring-boot-starter](moli-quick-codegen/moli-quick-codegen-spring-boot-starter/README.md) | 嵌入 API/UI，默认关闭 |
| [moli-quick-codegen-app](moli-quick-codegen/moli-quick-codegen-app/README.md) | 本机独立 UI，127.0.0.1:8088 |

### 对象存储

| 模块 | 用途 |
| --- | --- |
| [moli-quick-object-storage](moli-quick-object-storage/README.md) | 聚合父模块、多 Provider 接入总指南 |
| [moli-quick-object-storage-core](moli-quick-object-storage/moli-quick-object-storage-core/README.md) | 无 Spring/厂商依赖的统一接口 |
| [moli-quick-object-storage-spring-boot-autoconfigure](moli-quick-object-storage/moli-quick-object-storage-spring-boot-autoconfigure/README.md) | 客户端注册表自动配置 |
| [moli-quick-object-storage-oss](moli-quick-object-storage/moli-quick-object-storage-oss/README.md) | 阿里云 OSS |
| [moli-quick-object-storage-obs](moli-quick-object-storage/moli-quick-object-storage-obs/README.md) | 华为云 OBS |
| [moli-quick-object-storage-cos](moli-quick-object-storage/moli-quick-object-storage-cos/README.md) | 腾讯云 COS |
| [moli-quick-object-storage-s3](moli-quick-object-storage/moli-quick-object-storage-s3/README.md) | Amazon S3 / S3 兼容服务 |

### OAuth Client

| 模块 | 用途 |
| --- | --- |
| [moli-quick-oauth](moli-quick-oauth/README.md) | OAuth 功能聚合与宿主接入指南 |
| [moli-quick-oauth-client](moli-quick-oauth/moli-quick-oauth-client/README.md) | 客户端聚合与子模块导航 |
| [moli-quick-oauth-client-core](moli-quick-oauth/moli-quick-oauth-client/moli-quick-oauth-client-core/README.md) | 原生类型、非 Web 授权、身份/渠道接口 |
| [moli-quick-oauth-client-spring-boot-autoconfigure](moli-quick-oauth/moli-quick-oauth-client/moli-quick-oauth-client-spring-boot-autoconfigure/README.md) | 公共自动配置、Web configurer |
| [moli-quick-oauth-client-spring-boot-starter](moli-quick-oauth/moli-quick-oauth-client/moli-quick-oauth-client-spring-boot-starter/README.md) | 业务便捷入口，默认关闭 |
| [moli-quick-oauth-client-github](moli-quick-oauth/moli-quick-oauth-client/moli-quick-oauth-client-github/README.md) | GitHub.com OAuth App |
| [moli-quick-oauth-client-qq](moli-quick-oauth/moli-quick-oauth-client/moli-quick-oauth-client-qq/README.md) | QQ 互联网站登录 |
| [moli-quick-oauth-client-wechat](moli-quick-oauth/moli-quick-oauth-client/moli-quick-oauth-client-wechat/README.md) | 微信网站扫码及公众号网页授权 |
| [moli-quick-oauth-client-google](moli-quick-oauth/moli-quick-oauth-client/moli-quick-oauth-client-google/README.md) | Google OIDC 登录 |
| [moli-quick-oauth-client-jdbc](moli-quick-oauth/moli-quick-oauth-client/moli-quick-oauth-client-jdbc/README.md) | 长期授权 JDBC 存储 |
| [moli-quick-oauth-client-redis](moli-quick-oauth/moli-quick-oauth-client/moli-quick-oauth-client-redis/README.md) | 临时状态/长期授权/分布式刷新 |

### OAuth Authorization Server / Resource Server

| 模块 | 用途 |
| --- | --- |
| [moli-quick-oauth-auth-server](moli-quick-oauth/moli-quick-oauth-auth-server/README.md) | 授权服务器聚合与接入 |
| [moli-quick-oauth-auth-server-core](moli-quick-oauth/moli-quick-oauth-auth-server/moli-quick-oauth-auth-server-core/README.md) | 原生授权服务扩展、并发协调和受限快照编解码 |
| [moli-quick-oauth-auth-server-spring-boot-autoconfigure](moli-quick-oauth/moli-quick-oauth-auth-server/moli-quick-oauth-auth-server-spring-boot-autoconfigure/README.md) | 授权服务器的属性、密钥和安全链接入 |
| [moli-quick-oauth-auth-server-spring-boot-starter](moli-quick-oauth/moli-quick-oauth-auth-server/moli-quick-oauth-auth-server-spring-boot-starter/README.md) | 授权服务器应用依赖入口 |
| [moli-quick-oauth-auth-server-jdbc](moli-quick-oauth/moli-quick-oauth-auth-server/moli-quick-oauth-auth-server-jdbc/README.md) | MySQL/PostgreSQL 原生 JDBC 授权持久化 |
| [moli-quick-oauth-auth-server-redis](moli-quick-oauth/moli-quick-oauth-auth-server/moli-quick-oauth-auth-server-redis/README.md) | Redis/Sentinel 授权持久化与分布式协调 |
| [moli-quick-oauth-resource-server](moli-quick-oauth/moli-quick-oauth-resource-server/README.md) | 资源服务器聚合与接入 |
| [moli-quick-oauth-resource-server-core](moli-quick-oauth/moli-quick-oauth-resource-server/moli-quick-oauth-resource-server-core/README.md) | scope/role 权限映射内核 |
| [moli-quick-oauth-resource-server-spring-boot-autoconfigure](moli-quick-oauth/moli-quick-oauth-resource-server/moli-quick-oauth-resource-server-spring-boot-autoconfigure/README.md) | JWT/opaque 验证、配置器和默认 API 安全链 |
| [moli-quick-oauth-resource-server-spring-boot-starter](moli-quick-oauth/moli-quick-oauth-resource-server/moli-quick-oauth-resource-server-spring-boot-starter/README.md) | 资源服务器应用依赖入口 |

独立三应用联调见 [OAuth 示例](moli-quick-oauth/examples/README.md)。

## 按场景选择

- 统一 Web 错误、请求 ID：引入 Web Starter；正常成功响应仍由业务决定。
- 业务方法防重/限流：选择幂等、限流 Starter，多实例配置 Redis；注解方法应通过 Spring 代理调用。
- 数据访问：MyBatis-Plus 运行时组件与代码生成工具分别接入；默认分页方言为 MySQL，其他数据库配置 `moli.mybatis-plus.db-type` 或提供分页 Bean。
- 第三方登录：OAuth Client Starter + 按需渠道 + 宿主安全链/成功处理器；详细配置见 [QQ/微信/Google 渠道指南](moli-quick-oauth/CHANNELS.md)。Quick Security JWT 不会自动桥接第三方身份。
- 对象存储：直接添加实际 Provider，单/多 Provider 使用统一 ObjectStorageClient。
- 代码生成：本机 UI 用 codegen-app，嵌入应用用 codegen Starter，写项目用 CLI/Maven；首选统一生成器，旧模块保持独立 API。

## 默认开关与运行边界

| 能力 | 默认/启用方式 | 需要注意 |
| --- | --- | --- |
| AOP 日志/耗时 | 显式 `@EnableAop` 或单项启用注解 | Spring 代理调用；日志包含参数/返回值 |
| 幂等、限流、Web | 默认开启 | 自定义 Bean/配置可替换；内存存储仅当前实例 |
| Security/JWT | 默认开启，默认链在宿主已有链时退让 | JWT 启用必须显式配置随机密钥，缺失或旧默认值拒绝启动；自有链自行挂载 JWT 过滤器 |
| 请求验签 | `moli.interceptor.sign.sign.enable=true` | RedisUtil、签名协议一致性；缺少 Jackson 2 Mapper 时使用模块内部默认值 |
| 对象存储 Provider | 对应 enabled=true 或单 provider 选择项 | 需要实际 SDK 凭证、bucket 与权限 |
| 生成器 Web/UI | `moli.codegen.enabled=true` | 宿主访问控制；页面只预览/下载 |
| OAuth Authorization Server / Resource Server | 对应 `moli.oauth.auth-server.enabled=true` / `resource-server.enabled=true` | 默认关闭；Servlet、宿主认证、标准 Bearer 令牌；不自动桥接旧 JWT |
| OAuth Client | `moli.oauth.client.enabled=true` | 登录另开 login.enabled，宿主掌握本地登录态 |

各模块完整默认值及限制以其 README 和属性类为准。不要将演示应用、生成器或聚合父 POM 当成业务依赖入口。

Starter 安全默认值及接入调整见 [迁移说明](STARTER-MIGRATION.md)。

## 本地构建与验证

在仓库根目录执行（以下命令面向 JDK 17）：

```shell
mvn -B -ntp -Dgpg.skip=true -Dmaven.javadoc.skip=true test
mvn -B -ntp -Dgpg.skip=true -Dmaven.javadoc.skip=true install
mvn -B -ntp -pl :moli-quick-web-spring-boot-starter -am -Dgpg.skip=true test
```

使用 JDK 23+ 时附加 `-Dmaven.compiler.proc=full`，启用仓库现有 Lombok 注解处理；不必为文档修改运行整个构建。PowerShell 中包含点的 -D 参数必要时整体加引号。

生成器 Maven 构建默认执行 npm ci 和前端打包；仅验证后端可临时用 `-Dcodegen.ui.skip=true`，不能代替干净 UI 发布构建。

真实集成测试显式启用：

| 功能 | Profile / 说明 |
| --- | --- |
| 统一生成器数据库 CRUD、XML 与生成代码验证 | `database-it`；数据库环境变量、Boot 3 验证配置见 [生成器文档](moli-quick-codegen/README.md) |
| OAuth MySQL/PostgreSQL/Redis | `oauth-it`；使用专用测试库及 Redis，配置见 [OAuth 文档](moli-quick-oauth/README.md) |
| 云存储、真实第三方账号 | 需要对应平台凭证手工冒烟；本地协议测试不等同真实平台联调 |

集成测试不自动创建数据库服务，不应指向业务库。CI 配置见 [.github/workflows](.github/workflows)。

## 前端子项目

这两个项目不额外计入 Maven 模块数量：

- [生成器 UI](moli-quick-codegen/moli-quick-codegen-spring-boot-starter/src/main/frontend/README.md)：随 Starter 构建分发。
- [签名协议 Vue 演示](moli-quick-interceptors/src/main/vue/quick-interceptors-demo/README.md)：独立演示，不作为生产私钥管理方案。
