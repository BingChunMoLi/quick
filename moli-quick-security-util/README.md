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

默认 JWT 接入由宿主实现登录凭证认证和 `/login` 接口；白名单不会自动创建登录接口。显式启用表单登录时，Spring Security 提供 `/login` 流程，宿主提供 AuthenticationManager 或用户认证配置。HMAC 密钥至少需要满足算法的 256 bit 长度要求，使用随机强密钥。未替换内置默认密钥时会输出启动 WARN，不打印密钥内容。

默认链要求其他请求认证，默认关闭 CSRF、表单登录和 HTTP Basic；JWT 开启时使用 STATELESS 策略。基于 Cookie/Session 的业务应自行定义安全链及 CSRF 策略。声明自己的安全链后，Quick 不会自动将 JWT 过滤器加入该链，需要宿主显式添加：

```java
http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
```

其中 `JwtAuthenticationFilter` 位于 `com.bingchunmoli.security.jwt`。

默认链会在 Servlet 应用中启用所需的 Web Security 配置，宿主无需额外添加 `@EnableWebSecurity`。JWT 过滤器对损坏、过期或撤销的令牌返回 HTTP 401；需要定制失败响应时，可以使用接收 `AuthenticationEntryPoint` 的构造器声明自己的过滤器 Bean。

验签后会先校验 `jti`、`sub`、`exp`，SIMPLE/MIXIN 模式还要求非空 `sid`；字段类型错误和非法 `mode` 同样返回 401，不会先访问会话存储。历史令牌未携带 `mode` 时仍按当前配置模式处理。

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

默认 `InMemoryJwtSessionStore` 仅作用于当前进程。

内存存储每分钟清理过期会话和黑名单，查询时也检查有效期；强制退出的会话立即移除。Spring 会在关闭上下文时停止清理任务，通过 `new InMemoryJwtSessionStore()` 手动创建时应使用 try-with-resources 或调用 `close()`。

多实例可引入 Redis 支持并配置：

```yaml
moli:
  security:
    jwt:
      session-store-class-name: com.bingchunmoli.security.jwt.RedisJwtSessionStore
      redis-key-prefix: my-app:security:jwt
```

需要宿主的 `RedisUtil` 或 `RedisTemplate<String,Object>`；不是 OAuth 模块使用的 `StringRedisTemplate` 存储协议。也可提供 `JwtSessionStore` Bean，或配置其实现类名。

Redis 存储将会话撤销标记单独保存到 `<redis-key-prefix>:session:<sid>:revoked`，有效期覆盖会话剩余寿命，刷新会话不会删除或覆盖该标记。已有会话数据可继续读取；滚动升级时应完成所有实例升级后再依赖此并发退出保证，因为旧版本不读取撤销标记。

用户会话索引通过单 Key Lua 脚本原子添加成员并延长 TTL，短会话及并发保存不会缩短长会话的索引寿命；已有永久索引保持永久。会话、撤销标记和 Token 黑名单的 TTL 均向上取整，覆盖令牌的剩余有效期。

保存和按用户查询时会清理失效索引成员。新成员携带到期时间和独立版本，清理旧快照不会删除并发保存的新版本；重复保存同一会话会替换旧成员。旧版纯 Session ID 成员仍可读取和清理，新成员格式需要所有实例升级后识别。Redis 活跃时间刷新使用原始存储字节进行 CAS，并保留键的 TTL；Redis 和内存存储均忽略较早的活动时间，避免乱序刷新让活跃会话提前过期。

## 强制退出与配置

JWT 使用 `JwtTokenService.forceLogoutToken(token)`、`forceLogoutSession(sid)`、`forceLogoutByUserId(id)`。STATELESS 模式按用户退出不能替代逐 Token 黑名单。Session 登录使用 `SecurityLogoutService.logoutCurrent(request,response)` 或 `forceLogoutByUsername(name)`；SessionRegistry 需要实际注册业务会话，标记过期后由后续请求处理退出。

默认 HttpSession 安全上下文在保存时通过 Session 属性监听器同步登记，登录成功响应返回前即可按用户强退，兼容 Boot Security 宿主和自定义安全链。强退过滤器仍负责拒绝过期会话并更新请求活动。关闭 `force-logout-filter-enabled` 会同时禁用 Servlet 过滤器注册及 Session 登记监听器。使用自定义 SecurityContextRepository 且不把上下文保存到默认 HttpSession 属性时，由宿主登记业务会话。

本模块的登记、刷新及三种 Session 强退入口使用同一 `SessionRegistry` 实例的监视器协调，整个检查／写入过程互斥，旧登记不会覆盖并发强退的过期标记。宿主若直接调用同一注册表替换会话或执行过期操作，也应在 `synchronized (sessionRegistry)` 内完成相应检查和写入；该协调作用于同一进程中的实例，分布式注册表需自行提供跨实例的原子保证。

JWT 认证通过 `JwtSessionStore.refreshIfValid` 校验并续期当前会话。内存实现使用同一会话的原子计算，Redis 实现使用原始存储字节 CAS；发生竞争后重新校验最新状态，不会根据旧活跃期限无条件撤销已续期会话。接口保留兼容旧自定义实现的默认方法（刷新前后重查）；自定义分布式存储应覆盖该方法，以存储层条件操作保证校验与续期的原子性。

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

表中相对属性均位于 `moli.security` 下。关闭 `jwt.enabled` 时默认链不再依赖 JWT 过滤器；认证仍由宿主提供，可通过开启表单登录、HTTP Basic 或自定义安全链接入。自有链场景需自行决定 JWT/Session 处理。

第三方登录使用独立 [OAuth 模块](../moli-quick-oauth/README.md)；本模块不自动把 OAuth 身份桥接为本地 JWT。
