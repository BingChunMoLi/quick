# Quick OAuth Resource Server

[项目总览](../../README.md) · [OAuth 导航](../README.md) · [独立应用示例](../examples/README.md)

Java 17 / Boot 4.1.0 / Security 7.1.0，Quick `1.0.13-bate`。Servlet 资源服务器，使用 Spring 原生 Bearer 过滤器、JWT/Opaque AuthenticationProvider；不签发令牌，不提供 OAuth 登录。默认关闭。

| 子模块 | 用途 |
| --- | --- |
| [core](moli-quick-oauth-resource-server-core/README.md) | 可配置 scope/role 权限映射 |
| [autoconfigure](moli-quick-oauth-resource-server-spring-boot-autoconfigure/README.md) | decoder/introspector、配置器、默认 API 链与启动校验 |
| [starter](moli-quick-oauth-resource-server-spring-boot-starter/README.md) | 应用接入入口 |

## JWT 接入

导入 Quick BOM，添加 `com.bingchunmoli:moli-quick-oauth-resource-server-spring-boot-starter` 和宿主 MVC/Servlet 依赖。

```yaml
moli:
  oauth:
    resource-server:
      enabled: true
      mode: jwt
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.example.com
          jwk-set-uri: https://auth.example.com/oauth2/jwks
          audiences: [my-api]
```

Quick 默认 decoder 要求显式 issuer 和非空 audiences，校验签名、时间、issuer 及至少一个允许的 audience。缺少 JWK URI 时通过 issuer discovery；也可配置 public-key-location 指定 X.509 RSA 公钥。默认 RS256，JWK 模式可通过原生 jws-algorithms 配置算法列表；公钥模式仅配置一种算法。

## opaque / introspection

```yaml
moli:
  oauth:
    resource-server:
      enabled: true
      mode: opaque
      connect-timeout: 3s
      read-timeout: 5s
spring:
  security:
    oauth2:
      resourceserver:
        opaquetoken:
          introspection-uri: https://auth.example.com/oauth2/introspect
          client-id: resource-service
          client-secret: ${INTROSPECTION_CLIENT_SECRET}
```

使用原生 introspection，不缓存成功结果；连接/读取超时默认 3s/5s，不可用时拒绝。可以 introspect JWT，也可以 introspect reference token。JWT 模式本地验签不检查授权存储，因此撤销后可能仍有效至到期；需要逐请求确认撤销状态时使用 opaque 模式。

## 权限与默认链

默认只接受 `Authorization: Bearer`，不从 URL、表单或 Cookie 提取。默认主体 sub，scope（兼容 scp）映射为 SCOPE_ 权限。可配置 `principal-claim`、`scope-claim`、`scope-prefix`；`roles-claim` 默认未启用，显式配置后使用 `roles-prefix`（默认 ROLE_）。角色 claim 应提供未加前缀的角色名，或者显式设空前缀。

自定义 principal-claim 必须存在，值为非空字符串或数字；对象、数组、布尔值、空值会被拒绝并返回 invalid_token，不会转成字符串身份。opaque 模式使用默认 sub 且该字段缺失时，仍可使用原生 introspector 提供的非空主体名称。缺少 JWT audience 时同样返回 invalid_token。

默认启用方法权限，可用 `method-security-enabled=false` 关闭 Quick 的启用。接口示例：

```java
@PreAuthorize("hasAuthority('SCOPE_api.read')")
@GetMapping("/api/me")
public Map<String, String> me(Authentication authentication) {
    return Map.of("subject", authentication.getName());
}
```

默认 API 链仅在宿主没有安全链时创建：STATELESS、无请求缓存、关闭该链 CSRF/form/basic，默认请求均需认证；`permit-all` 可指定公共路径。缺少/无效令牌返回标准 401，权限不足返回 403，保留 WWW-Authenticate。

宿主提供安全链后，必须显式应用：

```java
http.with(quickResourceServerWebSupport.configurer(), Customizer.withDefaults());
```

类型位于 `com.bingchunmoli.oauth.server.resource.web`。此配置器不会改变宿主的 Session、CSRF 和 request-cache 策略；宿主混合 Cookie/Session 与 Bearer 时自行决定 CSRF。默认链关闭或已退让而未应用配置器会启动失败。暂不支持 WebFlux，显式在非 Servlet 应用启用会失败。

## 覆盖与共存

宿主 JwtDecoder 或 OpaqueTokenIntrospector Bean 优先。多个 decoder 时使用明确 Bean 名 `quickResourceJwtDecoder`；授权服务器保留自己的 `quickAuthorizationServerJwtDecoder`，避免同进程冲突。宿主自定义 decoder 负责其校验策略。

JWT 认证转换器使用 `quickResourceJwtAuthenticationConverter` Bean 名；opaque 使用 OpaqueTokenAuthenticationConverter。可替换 BearerTokenResolver；错误处理 Bean 名为 `quickResourceAuthenticationEntryPoint` / `quickResourceAccessDeniedHandler`。自定义实现承担自己的安全策略。

同应用使用授权服务器时，宿主须声明 API 和登录链，协议链 order=0，API 链例如 order=100、登录链 order=200。与 security-util 共存时，普通上下文工具可继续读取 Spring Authentication；仅使用标准 OAuth Bearer 的应用应显式设置 `moli.security.jwt.enabled=false`，不要把旧 JWT 过滤器挂到相同路径。旧模块默认值没有修改。

Quick 关闭时不替换原生 Boot 行为。开启时替换对应原生资源服务自动配置，避免额外默认链及 Redis 带入 Reactor 后的 Reactive introspection。没有引入数据库、Redis、Web 服务器或 security-util 运行依赖。
