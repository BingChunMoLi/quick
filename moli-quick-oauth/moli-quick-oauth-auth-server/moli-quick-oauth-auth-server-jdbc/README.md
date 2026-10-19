# moli-quick-oauth-auth-server-jdbc

MySQL/PostgreSQL 原生 JDBC 授权持久化。

[父模块完整指南](../README.md) · [独立示例](../../examples/README.md)

Java 17 / Spring Boot 4.1.0，Quick `1.0.13-bate`。先在宿主导入 Quick BOM，再按需添加：

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-auth-server-jdbc</artifactId>
</dependency>
```

提供原生 RegisteredClientRepository、OAuth2AuthorizationService、OAuth2AuthorizationConsentService；使用事务、授权行 NOWAIT 锁及有界重试，锁内重读原生 grant；原生 replay 检测产生的撤销会随协议失败正确提交。配置客户端仅插入缺失记录。

设置 `moli.oauth.auth-server.storage.type=jdbc`，由宿主提供 DataSource、驱动及连接配置。Quick 不自动建表，手工执行 [MySQL schema](src/main/resources/com/bingchunmoli/oauth/server/authorization/jdbc/schema-mysql.sql) 或 [PostgreSQL schema](src/main/resources/com/bingchunmoli/oauth/server/authorization/jdbc/schema-postgresql.sql)。脚本包含三张原生表及 quick_oauth_authorization_expiry；四张均需创建。MySQL 使用区分大小写的 utf8mb4_bin；PostgreSQL 使用 text/timestamptz。MySQL JDBC URL 采用脚本说明中的 UTC/preserveInstants 选项。

属性及令牌元数据的原生 JDBC 读写映射器对称使用 AuthorizationAttributeCodec，编码结果保存在版本化 JSON 外层中，不要求自定义 codec 返回 JSON。默认 codec 规范化集合，避免 JDK 不可变集合与原生 JSON 类型白名单不匹配，仍可读取原生格式的历史记录。自定义复杂主体时提供显式 codec，编码和解码必须保留对应属性及类型；不启用任意类型反序列化。每分钟清理过期授权，每批 100 条，与正在兑换/刷新的授权行互斥。需要替换底层存储时可直接提供宿主 OAuth2AuthorizationService Bean。

过期清理在持有授权行锁并重新确认到期时间后直接删除记录，不解码历史属性；失去 codec 支持或已删除客户端的过期记录不会阻塞整批清理。
