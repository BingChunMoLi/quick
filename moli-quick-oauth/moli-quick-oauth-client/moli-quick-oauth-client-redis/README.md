# moli-quick-oauth-client-redis

提供可独立选择的临时授权状态、长期 Token 存储和共享刷新协调器。

[项目总览](../../../README.md) · [Client 导航](../README.md) · [完整接入](../../README.md)

当前源码基线为 Java 17 / Spring Boot 4.1.0，Quick `1.0.13-bate`。先按项目总览导入 Quick BOM，再添加所需组件。

## 接入

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-client-redis</artifactId>
</dependency>
```

需要宿主配置 Redis 连接并提供 StringRedisTemplate，例如引入 Boot Redis Starter。模块不会创建 Web 服务器。

## 配置

```yaml
moli:
  oauth:
    client:
      enabled: true
      authorization-state:
        store: redis
        ttl: 10m
        cookie-secure: true
      authorized-client:
        store: redis
      refresh:
        coordination: auto
        wait-timeout: 15s
        lease: 30s
      redis:
        key-prefix: my-app:oauth2
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password: ${REDIS_PASSWORD:}
```

网页登录还需 login.enabled=true、宿主处理器及安全链。只需后台存储时不必启用登录。临时状态和长期存储可分别只选一个 Redis；key-prefix 用于应用/环境隔离，同一应用各实例保持一致，不能包含 Redis hash tag 花括号。

## 临时状态

每次授权独立保存请求、PKCE verifier/OIDC nonce，并通过短期 HttpOnly Cookie 绑定浏览器。GET/query 渠道使用 SameSite=Lax；Apple 等 form_post 渠道自动使用 SameSite=None + Secure，使跨站 POST 可以携带状态 Cookie。匹配后原子消费，拒绝过期和重放；模块不创建 HttpSession。宿主仍需自行配置业务链的请求缓存和 Session 策略。

默认 Secure Cookie，GET/query 渠道本地 HTTP 调试可显式关闭 cookie-secure；form_post 必须保持 cookie-secure=true 并使用 HTTPS。支持多标签页和跨实例回调。

## 长期存储与刷新

使用版本化数据格式，不使用 Java 对象序列化，不保存客户端密钥；Token 本身仍敏感，编码不等于加密。已知最终有效期时设置 TTL，访问/刷新令牌任一仍有效就保留；长期访问或未知刷新有效期不提前淘汰。重复登录不重新下发 refresh token 时保留同一授权的旧值，且不会错误缩短 TTL。

Redis 长期存储的 auto 模式使用共享协调。锁带所有者校验、续期、安全释放，保存/删除与所有权校验通过 Lua 原子执行。等待超时和丢失所有权直接失败，不绕过锁刷新；单次网络超时由宿主 Redis 客户端决定。

设置 refresh.coordination=local 只协调当前进程。JDBC 也可复用本模块的 Redis 协调，但没有跨数据库事务保障。真实 Redis 回归见 oauth-it profile，不能指向业务实例。
