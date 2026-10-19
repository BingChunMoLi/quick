# moli-quick-oauth-auth-server-redis

Redis/Sentinel 授权持久化与分布式协调。

[父模块完整指南](../README.md) · [独立示例](../../examples/README.md)

Java 17 / Spring Boot 4.1.0，Quick `1.0.13-bate`。先在宿主导入 Quick BOM，再按需添加：

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-auth-server-redis</artifactId>
</dependency>
```

依赖宿主 StringRedisTemplate，使用版本化 JSON/受限属性 codec、SHA-256 令牌摘要索引及 Lua 原子记录/索引变更。默认前缀 moli:oauth:authorization；不同应用/环境必须隔离。支持租约续期、所有者校验、安全释放，丢失持有权时禁止保存；不支持 Redis Cluster。

设置 `moli.oauth.auth-server.storage.type=redis`；`storage.wait-timeout=15s`、`storage.lease=30s`。客户端/同意不设 TTL；授权与索引按有效期清理，未知刷新到期时间不会提前删除。不保存明文客户端密钥或私钥，授权令牌数据仍需由宿主保护。

GrantCoordinator 的默认实现为 RedisGrantCoordinator，其持有权协议与 Redis 服务的写入脚本配套。替换协调实现时应同时替换兼容的授权服务；不能把进程内锁用于默认 Redis 服务而声称具备跨实例安全性。缺少模块/连接不会回退内存。

客户端记录的不可变 ID/client-id 关系在 Lua 脚本内校验，记录与索引原子更新。配置初始化使用 saveIfAbsent，多个实例同时启动也不会覆盖已存在的客户端设置。

授权更新及删除只清理仍属于当前授权的令牌摘要索引，保留已被其他授权重新占用的索引；已过期令牌不会重新建立索引，避免影响旧授权的撤销。
