# moli-quick-oauth-client-jdbc

使用原生 OAuth2AuthorizedClientService 接口，将长期授权和完整令牌时间信息写入 MySQL/PostgreSQL。

[项目总览](../../../README.md) · [Client 导航](../README.md) · [完整接入](../../README.md)

当前源码基线为 Java 17 / Spring Boot 4.1.0，Quick `1.0.13-bate`。先按项目总览导入 Quick BOM，再添加所需组件。

## 依赖与宿主数据源

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-client-jdbc</artifactId>
</dependency>
```

宿主还需 JDBC Starter、实际数据库驱动和 DataSource Bean；此模块的 MySQL/PostgreSQL 驱动是测试依赖，不向业务传递。浏览器/后台入口仍由公共 Starter 提供。

## 手动建表

先在专用业务授权库执行其中一个脚本：

- [MySQL](src/main/resources/com/bingchunmoli/oauth/client/jdbc/schema-mysql.sql)
- [PostgreSQL](src/main/resources/com/bingchunmoli/oauth/client/jdbc/schema-postgresql.sql)

不自动建表，也不使用 Spring 原生 OAuth 授权表结构。存储快照是编码而非加密，宿主负责访问控制及静态/传输加密。

## 配置

```yaml
moli:
  oauth:
    client:
      enabled: true
      authorized-client:
        store: jdbc
spring:
  datasource:
    url: ${OAUTH_DB_URL}
    username: ${OAUTH_DB_USERNAME}
    password: ${OAUTH_DB_PASSWORD}
```

客户端的 registration/provider 与普通接入相同。显式选择 jdbc 后缺少 DataSource 或模块会失败，不退回内存。宿主自定义 OAuth2AuthorizedClientService Bean 优先。

## 刷新与部署

默认仅提供进程内协调；多实例需要同时添加 Redis 模块、StringRedisTemplate 并设置 `moli.oauth.client.refresh.coordination=redis`。这不提供数据库与 Redis 之间的原子事务。

仅负责长期 Token，不决定临时登录状态用 Session 还是 Redis；两者独立选择。保存授权范围和访问/刷新令牌的 issued/expires 时间，不存客户端密钥。真实数据库回归使用 oauth-it profile，环境变量及专用库要求见完整接入文档。
