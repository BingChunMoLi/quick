# Quick OAuth Authorization Server

[项目总览](../../README.md) · [OAuth 导航](../README.md) · [独立应用示例](../examples/README.md)

Java 17 / Spring Boot 4.1.0 / Spring Security 7.1.0，Quick 版本 `1.0.13-bate`。基于 Spring 原生 Authorization Server，支持授权码 S256 PKCE、机密客户端刷新令牌、client_credentials、JWT/opaque access token、撤销、introspection，以及 OIDC discovery、ID Token、UserInfo 和标准退出。Servlet 专用。

## 模块与依赖

导入 Quick BOM，添加 `com.bingchunmoli:moli-quick-oauth-auth-server-spring-boot-starter`，由宿主添加 MVC/Servlet 服务器。

| 子模块 | 用途 |
| --- | --- |
| [core](moli-quick-oauth-auth-server-core/README.md) | 原生授权接口、grant 协调、白名单属性编解码和内存过期清理 |
| [autoconfigure](moli-quick-oauth-auth-server-spring-boot-autoconfigure/README.md) | 原生配置映射、密钥、默认安全链和启动校验 |
| [starter](moli-quick-oauth-auth-server-spring-boot-starter/README.md) | 应用接入入口 |
| [jdbc](moli-quick-oauth-auth-server-jdbc/README.md) | MySQL/PostgreSQL 持久化与事务协调 |
| [redis](moli-quick-oauth-auth-server-redis/README.md) | Redis/Sentinel 持久化与共享 grant 协调 |

## 最小配置

```yaml
moli:
  oauth:
    auth-server:
      enabled: true
      login:
        enabled: false
      signing:
        jwk-set-location: file:/run/secrets/oauth-signing.jwks
        active-key-id: signing-2026
      audiences:
        api-client: [my-api]
spring:
  security:
    oauth2:
      authorizationserver:
        issuer: https://auth.example.com
        client:
          api:
            registration:
              client-id: api-client
              client-secret: ${OAUTH_CLIENT_SECRET_HASH}
              client-authentication-methods: [client_secret_basic]
              authorization-grant-types: [client_credentials]
              scopes: [api.read]
```

客户端密钥配置为 `{bcrypt}...` 等带算法标识的密码哈希，调用方发送原始密钥。Quick 拒绝明文及 `{noop}` 客户端密钥。表单登录开启时宿主必须提供 UserDetailsService、AuthenticationProvider 或 AuthenticationManager；不创建默认用户。仅机器授权时保持 login.enabled=false。

原生配置前缀为 `spring.security.oauth2.authorizationserver`，不是 Client 模块的 registration/provider。支持原生 issuer、端点、client.registration、ClientSettings 与 TokenSettings。授权码默认要求 PKCE，原生校验仅接受 S256；Quick 将未显式配置的 `token.reuse-refresh-tokens` 默认设为 false。公开客户端遵循 Spring 原生刷新限制，client_credentials 不发刷新令牌。

audiences 按真正的 client-id 配置，不是 YAML 中的注册别名。缺少已配置客户端的 audience 时启动失败。JWT 与 opaque access token 均携带 audience。额外用户资料/角色通过原生 `OAuth2TokenCustomizer<JwtEncodingContext>` 或 `OAuth2TokenCustomizer<OAuth2TokenClaimsContext>` Bean 显式提供，默认不复制全部用户属性。

## 密钥与轮换

支持外部 JWK Set、PKCS12 或宿主 `JWKSource<SecurityContext>` Bean。配置加载器要求 RSA 至少 2048 bit、非空且唯一的 kid，并且只选择一个活动私钥。JWKS 仅发布公钥。JWK 文件可包含当前私钥及旧公钥；切换活动 kid 后重启实例，旧公钥保留到存量令牌到期。多实例必须加载一致的持久密钥。

PKCS12 使用 `signing.key-store-location`、`key-store-password`、`key-alias`，密钥库方案不同时设置 jwk-set-location。密钥库的其他 RSA 证书作为旧公钥保留。宿主 JWKSource/自定义 TokenGenerator 负责自身密钥有效性策略。

缺少密钥默认启动失败。开发时可明确设 `signing.development-ephemeral=true`；会告警且重启失效。私钥不进入 JDBC/Redis。暂不提供热轮换后台。

## 默认安全链与宿主接入

`enabled=false` 默认关闭。启用后，默认协议链 order=0，只匹配原生协议端点；可用 `order` 调整。标准端点包括 `/oauth2/authorize`、`/oauth2/token`、`/oauth2/introspect`、`/oauth2/revoke`、`/oauth2/jwks`、`/userinfo`、`/connect/logout` 和 discovery，具体可通过原生 settings 覆盖。

宿主没有业务链时提供兜底链：login.enabled=true 使用默认表单及同意页，否则拒绝协议外请求。协议链不影响业务链的 Session/CSRF。宿主声明任一业务链后，该兜底链退让，宿主应完整负责登录与业务路由。

禁用 `default-filter-chain-enabled` 后，宿主提供专门的协议链：

```java
@Bean
@Order(0)
SecurityFilterChain protocol(HttpSecurity http, QuickAuthorizationServerWebSupport support) {
    var configurer = support.configurer();
    http.securityMatcher(configurer.endpointsMatcher());
    http.authorizeHttpRequests(r -> r.anyRequest().authenticated());
    http.with(configurer, Customizer.withDefaults());
    return http.build();
}
```

`QuickAuthorizationServerWebSupport` 位于 `com.bingchunmoli.oauth.server.authorization.web`。默认链关闭却未应用配置器时启动失败。授权端和资源端同应用时保留协议链，宿主另外声明 API/登录链，并对 API 链应用资源配置器；不能依赖两套默认业务链。

用户登录仍属于宿主账户体系，可使用已有登录，也可复用 Quick OAuth Client 的成功处理器：按 provider + subject 关联本地账户，轮换业务 Session ID，保存本地 Authentication，再回到授权请求。禁止把第三方 Token 当作业务登录凭证或按邮箱直接合并账户。

## 存储、并发与清理

`storage.type=memory|jdbc|redis`，默认 memory。三种实现均提供 RegisteredClientRepository、OAuth2AuthorizationService、OAuth2AuthorizationConsentService；宿主对应 Bean 优先。原生 TokenGenerator、Settings、JWKSource 也可替换；授权端专用 decoder Bean 名为 `quickAuthorizationServerJwtDecoder`。

配置的客户端具有稳定 ID。持久库仅初始化缺失客户端，不在每次启动覆盖原记录；修改已有客户端由宿主通过原生 repository 管理。客户端和同意记录没有自动 TTL。

内存/JDBC 每分钟清理到期授权，JDBC 每批最多 100 条；Redis 为主记录与摘要索引设置 TTL。未完成授权最多保留十分钟；已知 token 全部到期后可删除，存在未知到期时间时不提前清理。JDBC 清理依赖脚本内的 Quick expiry 表。

grant 协调覆盖原生同意提交、授权码兑换、刷新和撤销：内存进程内锁；JDBC 在事务内锁授权行并重新读取；Redis 使用所有者校验、续租和 Lua 原子持久化。同意状态在锁内重新读取，同一状态并发提交仅允许签发一个授权码。默认等待 15s、Redis 租约 30s，`storage.wait-timeout` / `storage.lease` 可调整。超时或失去锁时拒绝，不绕过协调签发令牌。Redis 仅支持单实例/Sentinel，暂不支持 Cluster。

长期授权存储不等于浏览器 Session 存储。多实例登录需宿主配置共享 Spring Session 或相应路由。数据库、Redis 中的令牌属于敏感数据；编解码不等于加密。

## 验证与边界

首版不含用户/客户端管理后台、密码授权、隐式授权、设备授权和动态注册。不会桥接 security-util JWT。

新增协议测试位于 Starter 测试源码，使用四个真实 HTTP 应用验证 Authorization Server、JWT/opaque Resource Server 和现有 Quick Client。`-Poauth-it` 使用真实 MySQL/PostgreSQL/Redis 测试恢复、双实例竞争、超时、锁失效、续租和清理，不能以 H2 代替。
