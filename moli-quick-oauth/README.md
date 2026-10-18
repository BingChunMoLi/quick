# OAuth Client Quick Starter

Java 17 / Spring Boot 4.1，基于 Spring Security OAuth2。首版包含浏览器授权码登录、OIDC、用户授权 API 调用、后台 `client_credentials`、GitHub.com OAuth App，以及可选 JDBC / Redis 存储。

## 依赖与最小接入

项目通过 `moli-quick-dependencies` 管理版本；未使用该 BOM 时为各 Quick 依赖指定相同版本。

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-client-spring-boot-starter</artifactId>
</dependency>
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-client-github</artifactId>
</dependency>
```

Web 应用自行引入 MVC/Servlet 服务器。Starter 不强制引入 Web 服务器、Redis、JDBC 或 `moli-quick-security-util`。

```yaml
moli:
  oauth:
    client:
      enabled: true
      login:
        enabled: true
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: ${GITHUB_CLIENT_ID}
            client-secret: ${GITHUB_CLIENT_SECRET}
```

默认授权地址 `/oauth2/authorization/github`，回调地址 `{baseUrl}/login/oauth2/code/github`。在 GitHub OAuth App 中登记相同回调地址。反向代理后的公共地址应由宿主可信代理配置决定，不能直接信任来自任意客户端的 Forwarded 头。

宿主提供安全链和成功处理器：

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http, QuickOAuth2WebSupport oauth) {
    http.authorizeHttpRequests(r -> r
        .requestMatchers("/oauth2/authorization/**", "/login/oauth2/code/**").permitAll()
        .anyRequest().authenticated());
    http.with(oauth.configurer(), Customizer.withDefaults());
    return http.build();
}

@Bean
OAuth2LoginHandler oauth2LoginHandler() {
    return (identity, nativeUser, authorization, request, response) -> {
        // 在此查找/绑定本地账户、创建业务登录态并写响应。
        // identity.subject() 是外部身份；authorization 用于后续第三方 API 调用。
        response.setStatus(204);
    };
}
```

相关类型位于 `com.bingchunmoli.oauth.client` 和 `.web` 包。登录要求 Servlet Web 应用；非 Web 或 Reactive 应用启用登录、缺少处理器或未应用 configurer 时启动失败。仅需后台 API 调用时不启用 `login.enabled`，也不需要 Web 安全链或处理器。

Quick 不持久化第三方 Authentication 作为业务登录。授权跳转所用 Session 与业务登录 Session 是两个概念；默认只将临时授权请求保存在 Session。

## 宿主 Session / JWT

Session 登录回调中，宿主完成账户映射后，使用自己的 `SecurityContextRepository` 显式保存业务 Authentication：

```java
var context = SecurityContextHolder.createEmptyContext();
context.setAuthentication(businessAuthentication); // 宿主认证后的账户和权限
SecurityContextHolder.setContext(context);
businessSecurityContextRepository.saveContext(context, request, response);
response.sendRedirect("/"); // 固定或经宿主白名单验证的地址
```

`businessSecurityContextRepository` 应与宿主安全链保持一致，例如 `HttpSessionSecurityContextRepository`。宿主还负责其业务会话固定攻击防护策略。

JWT 登录回调由宿主 Token 服务签发本地令牌，通过自己的响应格式或 HttpOnly Cookie 返回。第三方 access/refresh token 不应返回浏览器，也不能放在重定向 URL。Quick 不依赖既有 JWT 模块；同时使用 `security-util` 时，宿主提供的安全链使其默认链回退，按宿主现有方式加入 JWT 过滤器。

不要直接按邮箱合并账户；应按渠道及稳定用户标识关联。GitHub 使用数字 ID，通用 OIDC 身份包含 issuer 和 subject。

## 配置与持久化

默认值：Quick 关闭、登录关闭、临时状态 `session`、长期令牌 `memory`、刷新协调 `auto`，等待 15 秒、Redis 租约 30 秒。存储类型及刷新协调选项不区分大小写。

```yaml
moli:
  oauth:
    client:
      enabled: true
      login:
        enabled: true
        authorization-base-uri: /oauth2/authorization
        callback-base-uri: /login/oauth2/code/*
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
```

自定义回调路径时同时设置原生 registration 的 `redirect-uri`。本地 HTTP 调试显式设 `cookie-secure: false`；生产 HTTPS 保持默认 true。Redis 临时状态通过每次授权独立的 HttpOnly / SameSite=Lax Cookie 绑定浏览器，支持多标签页、跨实例回调及一次性消费，适用于宿主 `STATELESS` 安全链。首版以 GET/query 授权回调为目标。

选择 Redis 需增加 `moli-quick-oauth-client-redis`，由宿主提供 `StringRedisTemplate` 和连接配置，例如引入 Boot 的 Redis Starter。共享 Redis 时必须为不同应用/环境配置不同的 `redis.key-prefix`（默认 `moli:oauth2`），同一应用各实例保持一致。临时状态和长期令牌可独立选用 Redis。Redis 长期存储默认启用共享刷新协调；JDBC 需分布式协调时添加 Redis 模块并设置 `refresh.coordination: redis`。`local` 仅协调当前进程；没有分布式协调的 JDBC 多实例部署可能同时刷新同一授权。

选择 JDBC 需增加 `moli-quick-oauth-client-jdbc`，提供 `DataSource`，设置 `authorized-client.store: jdbc`。在目标库手工执行该 JAR 的 `com/bingchunmoli/oauth/client/jdbc/schema-mysql.sql` 或 `schema-postgresql.sql`，Quick 不自动建表。不要与 Spring 原生 JDBC 授权表混用。

长期令牌采用带版本的数据编码，不保存客户端密钥。令牌数据本身是敏感数据，存储编码不提供加密；由宿主控制数据库/Redis 访问及传输保护。Redis 根据 access/refresh token 中较晚的已知有效期设置 TTL；长期 access token 或未知有效期的 refresh token 不提前淘汰，可通过 API 显式移除。refresh token 已过期时，仍有效的 access token 可继续使用至到期，再要求重新授权。内存存储适用于单实例或开发，重启丢失授权。

选择不存在的存储模块、缺少连接 Bean、无效配置时启动失败。宿主自定义 Bean 优先于默认实现。Quick 显式启用时仅排除 Boot 原生 OAuth2 Web 自动配置，自己提供授权仓库；不创建业务安全链，不改变宿主整体 Session/CSRF 规则。关闭 Quick 不干预原生 Boot 行为。

## 调用 API 与刷新

```java
AuthorizedClientRef ref = savedAuthorizationReference; // 登录回调交付，由宿主关联账户
RestClient api = quickOAuth2Client.restClient(ref,
    RestClient.builder().baseUrl("https://api.github.com"));
String user = api.get().uri("/user").retrieve().body(String.class);
quickOAuth2Client.remove(ref); // 移除本地授权；不代表远端撤销授权
```

RestClient 为特定可信 API 专用，勿接受用户输入的任意目标 URL。显式授权引用避免后台任务误用当前请求的用户。业务请求不会自动重试。

通用应用授权使用 `quickOAuth2Client.application("service")` 取得稳定应用引用，原生配置指定 `authorization-grant-type: client_credentials` 和 Provider `token-uri`。GitHub OAuth App 不支持此授权类型；GitHub App installation token 不在本模块范围内。

刷新持锁后重读令牌，Redis 租约续期并检查所有者，Redis 保存与所有权检查通过同一 Lua 脚本执行。等待超时、Redis 故障、所有权丢失均失败；不承诺在网络分区或外部服务已处理请求但响应丢失时“恰好一次”。JDBC 配合 Redis 时不提供跨数据库/Redis 的原子事务。`invalid_grant` 清理失效授权；调用方收到 `AuthorizationRequiredException` 后引导用户重新授权。

刷新锁等待期限计入 Redis 取锁请求的往返耗时，迟到的成功响应会释放锁并失败。协调器关闭后拒绝新取锁请求；关闭与取锁并发时，也会清理已获得的锁及线程状态。Redis 单次连接/命令超时由宿主 Redis 客户端配置控制。

## GitHub 与新增渠道

GitHub 默认 scope 为 `read:user`，自动使用 S256 PKCE。`moli.oauth.client.github.fetch-email: true` 为默认 scope 增加 `user:email`，并读取经过验证的邮箱；显式 scope 不会被覆盖。`offline_access` 可在原生 scope 中按需增加。兼容长期 access token 和带 `expires_in` / `refresh_token_expires_in` 的响应。

同一渠道的多个 registration 可配置 `provider: github`。使用宿主自定义 registration 仓库时，可通过 `moli.oauth.client.provider-mappings.<registrationId>: github` 指定适配器。

QQ、微信等新渠道创建独立模块，实现 `OAuth2ProviderAdapter` 并注册 Bean；通过 AutoConfiguration.imports 注册，适配默认端点、请求参数、Token 转换、用户服务和身份映射。无需修改 Core 或 Starter。适配器 ID 必须唯一，显式配置覆盖渠道默认值。通用 OAuth2/OIDC 可直接使用原生端点/issuer 配置，不必创建渠道模块。

可替换原生 registration/service/manager/provider Bean；Web 用户服务分别使用 Bean 名 `quickOAuth2UserService`、`quickOidcUserService`，失败处理器使用 `quickOAuth2FailureHandler`。保留 OIDC 原生用户及 ID Token。宿主声明对应泛型的 `OAuth2AccessTokenResponseClient` Bean，可分别覆盖授权码、刷新和应用授权的 Token 客户端，优先于渠道默认值；只覆盖一个授权类型时，其他类型仍使用渠道或 Spring 默认实现。自定义 `ProviderTokenClients` 或渠道 Token 客户端可复用宿主 HTTP 配置，并负责保留所需的渠道 Token 转换行为。

## 测试与真实 GitHub 冒烟

普通测试使用本地模拟 OAuth/OIDC 服务，不访问真实 GitHub。真实存储集成测试使用独立数据库，不能指向业务库：

```text
OAUTH_TEST_MYSQL_URL=jdbc:mysql://127.0.0.1:3306/oauth_test?allowPublicKeyRetrieval=true&useSSL=false
OAUTH_TEST_MYSQL_USERNAME=oauth_test
OAUTH_TEST_MYSQL_PASSWORD=...
OAUTH_TEST_POSTGRESQL_URL=jdbc:postgresql://127.0.0.1:5432/oauth_test
OAUTH_TEST_POSTGRESQL_USERNAME=oauth_test
OAUTH_TEST_POSTGRESQL_PASSWORD=...
OAUTH_TEST_REDIS_PORT=6379
```

```bash
mvn -B -ntp -pl :moli-quick-oauth-client-github,:moli-quick-oauth-client-jdbc,:moli-quick-oauth-client-redis,:moli-quick-oauth-client-spring-boot-starter -am -Poauth-it -Dgpg.skip=true -Dmaven.javadoc.skip=true verify
```

上述命令使用 JDK 17；本仓库使用 JDK 23+ 构建时额外添加 `-Dmaven.compiler.proc=full` 以启用现有模块的注解处理。JDBC 测试要求授权表不存在，创建成功后才负责删除；Redis 测试使用独立随机授权标识，不执行 FLUSHDB。CI 提供真实 MySQL/PostgreSQL/Redis 服务。

手工冒烟：创建 GitHub OAuth App、设置回调和环境变量、启动宿主、访问授权入口；检查成功回调数字 ID、宿主登录响应和服务端 API 调用。再切换 Redis 临时状态及 STATELESS，确认浏览器没有 JSESSIONID，重放回调失败；分别验证长期令牌和启用过期令牌的 App。不要将真实密钥或 Token 提交到仓库。
