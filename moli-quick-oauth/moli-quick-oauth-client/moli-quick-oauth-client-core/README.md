# moli-quick-oauth-client-core

OAuth2 核心契约和非 Web 授权能力，使用 Spring Security 原生 OAuth2 类型及 Spring HTTP，不依赖 Boot 自动配置或 Servlet API。

[项目总览](../../../README.md) · [Client 导航](../README.md) · [完整接入](../../README.md)

当前源码基线为 Java 17 / Spring Boot 4.1.0，Quick `1.0.13-bate`。先按项目总览导入 Quick BOM，再添加所需组件。

## 引入

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-client-core</artifactId>
</dependency>
```

## 核心类型

| 类型 | 用途 |
| --- | --- |
| `OAuth2ProviderAdapter/Registry` | 默认配置、Token 客户端、用户服务、身份归一化 |
| `OAuth2Identity` | 渠道和稳定外部标识、可选资料及原始属性 |
| `AuthorizedClientRef` | registrationId + principalName 定位授权 |
| `QuickOAuth2Client` | 获取/刷新/移除授权，构建 Bearer RestClient |
| `CoordinatedAuthorizedClientManager` | 取锁后重读授权、保存与 invalid_grant 清理 |
| `RefreshCoordinator` | 协调扩展，内置本地分段锁 |
| `TokenSnapshotCodec` | 版本化数据快照，不包含客户端密钥 |

## 非 Web 应用授权示例

```java
import com.bingchunmoli.oauth.client.*;
import java.time.Duration;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

var registration = ClientRegistration.withRegistrationId("billing")
    .clientId("client-id").clientSecret(System.getenv("OAUTH_CLIENT_SECRET"))
    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
    .tokenUri("https://id.example.test/oauth/token").build();
var registrations = new InMemoryClientRegistrationRepository(registration);
var clients = new InMemoryOAuth2AuthorizedClientService(registrations);
var coordinator = new LocalRefreshCoordinator(Duration.ofSeconds(15));
var provider = OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build();
var manager = new CoordinatedAuthorizedClientManager(registrations, clients, provider, coordinator);
var quick = new QuickOAuth2Client(manager, clients, coordinator);
var authorization = quick.authorize(quick.application("billing"));
```

配置示例域名和凭证后再执行。本例不创建 Spring 容器，不需要 HttpSession；浏览器登录应使用 Starter 和宿主安全链。

## 行为边界

后台授权缺失/过期返回明确异常，不跳转浏览器。`restClient(ref,builder)` 每次业务请求前获取令牌，不自动重试业务请求，只应用于固定可信 API。原始令牌只在服务端使用。

本地协调只覆盖当前 JVM；Redis 分布式协调与持久化由独立模块提供。当前 API 不实现 Authorization Server、Resource Server 或自动创建本地用户。渠道扩展和模板 Bean 名见完整接入指南。
