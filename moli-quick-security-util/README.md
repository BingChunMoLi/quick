# moli-quick-security-util

提供 Spring Security 当前用户读取、Session 强制退出和 JWT 签发/校验工具。默认安全链会在宿主已有 SecurityFilterChain 时退让。

[项目总览](../README.md)

当前源码：Java 17，Spring Boot 依赖管理 4.1.0，Quick 版本 `1.0.13-bate`。依赖示例使用项目总览中的统一版本管理；工具和聚合模块的特殊接入方式见下文。

## 引入依赖

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-security-util</artifactId>
</dependency>
```

## 最小配置

```yaml
moli:
  security:
    enabled: true
    default-filter-chain-enabled: true
    permit-all:
      - /error
      - /login
    jwt:
      enabled: true
      mode: SIMPLE
      issuer: moli-quick
      secret: ${JWT_SECRET}
      timeout: 2h
      active-timeout: 30m
```

宿主自行实现登录凭证认证和 `/login` 接口；白名单不会自动创建登录接口。HMAC 密钥至少需要满足算法的 256 bit 长度要求，使用随机强密钥。未替换内置默认密钥时会输出启动 WARN，不打印密钥内容。

默认链要求其他请求认证，默认关闭 CSRF、表单登录和 HTTP Basic；JWT 开启时使用 STATELESS 策略。基于 Cookie/Session 的业务应自行定义安全链及 CSRF 策略。声明自己的安全链后，Quick 不会自动将 JWT 过滤器加入该链，需要宿主显式添加：

```java
http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
```

其中 `JwtAuthenticationFilter` 位于 `com.bingchunmoli.security.jwt`。

## 签发与读取

```java
import com.bingchunmoli.security.jwt.JwtToken;
import com.bingchunmoli.security.jwt.JwtTokenService;
import com.bingchunmoli.security.jwt.JwtUser;
import java.util.List;
import java.util.Map;

// 必须在业务凭证认证通过后调用；通过构造器注入 JwtTokenService tokens
JwtToken token = tokens.createToken(new JwtUser(
    "user-1", "alice", List.of("ROLE_USER"), Map.of("tenantId", "tenant-1")));
String bearerToken = token.getToken();
tokens.authenticate(bearerToken);
```

客户端默认发送 `Authorization: Bearer <token>`。`SecurityUserContext` 提供 `getAuthentication()`、`getUsername()`、`getPrincipal(Class)`、`hasRole/hasAuthority` 等方法；无登录态时返回 Optional 或 false。

## JWT 模式与存储

| 模式 | 用户信息 | 服务端状态 |
| --- | --- | --- |
| `SIMPLE`（默认） | 主要存于服务端会话 | 校验会话、活跃期限 |
| `MIXIN` | 令牌含用户数据 | 仍校验服务端会话 |
| `STATELESS` | 令牌含用户数据 | 不依赖登录会话，但校验令牌黑名单 |

默认 `InMemoryJwtSessionStore` 仅作用于当前进程。多实例可引入 Redis 支持并配置：

```yaml
moli:
  security:
    jwt:
      session-store-class-name: com.bingchunmoli.security.jwt.RedisJwtSessionStore
      redis-key-prefix: my-app:security:jwt
```

需要宿主的 `RedisUtil` 或 `RedisTemplate<String,Object>`；不是 OAuth 模块使用的 `StringRedisTemplate` 存储协议。也可提供 `JwtSessionStore` Bean，或配置其实现类名。

## 强制退出与配置

JWT 使用 `JwtTokenService.forceLogoutToken(token)`、`forceLogoutSession(sid)`、`forceLogoutByUserId(id)`。STATELESS 模式按用户退出不能替代逐 Token 黑名单。Session 登录使用 `SecurityLogoutService.logoutCurrent(request,response)` 或 `forceLogoutByUsername(name)`；SessionRegistry 需要实际注册业务会话，标记过期后由后续请求处理退出。

| 属性 | 默认值 |
| --- | --- |
| `moli.security.enabled` | `true` |
| `default-filter-chain-enabled` | `true` |
| `csrf-enabled/form-login-enabled/http-basic-enabled` | `false` |
| `force-logout-filter-enabled` | `true` |
| `expired-status` | `401` |
| `jwt.enabled` | `true` |
| `jwt.mode / timeout / active-timeout` | `SIMPLE / 2h / 30m` |
| `jwt.header-name / token-prefix` | `Authorization / "Bearer "` |

表中相对属性均位于 `moli.security` 下。默认安全链工厂依赖 JWT 过滤器。关闭 `jwt.enabled` 时应同时关闭 `default-filter-chain-enabled` 并提供宿主安全链，避免缺少过滤器 Bean；自有链场景也需自行决定 JWT/Session 处理。

第三方登录使用独立 [OAuth 模块](../moli-quick-oauth/README.md)；本模块不自动把 OAuth 身份桥接为本地 JWT。
