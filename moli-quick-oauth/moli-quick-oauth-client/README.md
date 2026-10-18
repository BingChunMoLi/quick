# OAuth Client 使用说明

[项目总览](../../README.md)

当前源码版本 `1.0.13-bate`，Java 17 / Spring Boot 依赖管理 4.1.0；下方省略版本的依赖示例需先导入项目总览中的 Quick BOM。


本模块为 Spring Boot 应用提供 OAuth 2.0 / OIDC 客户端能力，基于 Spring Security OAuth2 Client。支持浏览器授权码登录、第三方 API 调用、`client_credentials` 应用授权，以及可选的 JDBC / Redis 授权存储。

## 模块选择

| Maven artifact | 用途 |
| --- | --- |
| `moli-quick-oauth-client-spring-boot-starter` | 常规接入入口，包含自动配置和核心客户端 |
| `moli-quick-oauth-client-github` | GitHub 渠道适配、默认 `read:user` scope 和 PKCE |
| `moli-quick-oauth-client-qq` | QQ 互联网站登录、OpenID 校验与令牌刷新 |
| `moli-quick-oauth-client-wechat` | 网站扫码及公众号网页授权（userinfo/base） |
| `moli-quick-oauth-client-google` | Google OIDC、PKCE 与可选离线授权 |
| `moli-quick-oauth-client-jdbc` | 将长期授权令牌保存到 JDBC 数据库 |
| `moli-quick-oauth-client-redis` | 将临时授权状态或长期令牌保存到 Redis，并支持分布式刷新协调 |
| `moli-quick-oauth-client-core` | OAuth 客户端核心 API，通常由 Starter 间接引入 |
| `moli-quick-oauth-client-spring-boot-autoconfigure` | 自动配置实现，通常由 Starter 间接引入 |

使用仓库 BOM 管理版本；未导入 BOM 时，将所有 `moli-quick-oauth-client-*` 依赖统一为同一版本。

## 快速接入：GitHub 浏览器登录

添加依赖：

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

宿主应用需要自行提供 Spring MVC / Servlet 运行环境和安全链。配置 OAuth App 的客户端凭证，并在 GitHub 登记回调地址：

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

默认授权入口为 `/oauth2/authorization/github`，回调地址为 `{baseUrl}/login/oauth2/code/github`。本地启动时，OAuth App 中登记的回调地址必须与应用实际回调地址一致。

宿主需允许 OAuth 入口和回调访问，并应用 Quick 提供的安全配置器：

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http,
                                        QuickOAuth2WebSupport oauth) throws Exception {
    http.authorizeHttpRequests(r -> r
        .requestMatchers("/oauth2/authorization/**", "/login/oauth2/code/**").permitAll()
        .anyRequest().authenticated());
    http.with(oauth.configurer(), Customizer.withDefaults());
    return http.build();
}

@Bean
OAuth2LoginHandler oauth2LoginHandler() {
    return (identity, nativeUser, authorization, request, response) -> {
        // 根据 identity 将外部身份关联到本地账户，并建立宿主自己的登录态。
        // authorization 可由宿主关联到本地用户，供后续调用第三方 API。
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    };
}
```

登录成功处理器负责将外部身份映射到本地账户，并创建宿主自己的 Session 或 JWT。Quick 不会把第三方认证直接作为业务登录态，也不会替宿主签发业务令牌。建议按渠道和稳定用户标识关联账户；不要只凭邮箱自动合并。

## 调用第三方 API

从登录回调获得的 `AuthorizedClientRef` 应由宿主安全地关联到本地用户或授权记录。之后可显式使用该引用创建 API 客户端：

```java
AuthorizedClientRef ref = savedAuthorizationReference;
RestClient api = quickOAuth2Client.restClient(ref,
    RestClient.builder().baseUrl("https://api.github.com"));
String user = api.get().uri("/user").retrieve().body(String.class);

quickOAuth2Client.remove(ref); // 删除本地授权记录，不会撤销远端授权
```

将 `RestClient` 固定到可信的第三方 API 域名，不要把任意用户输入作为目标 URL。Quick 会在请求需要时处理令牌刷新；业务 API 请求不会自动重试。刷新授权失效时，调用方应捕获 `AuthorizationRequiredException` 并引导用户重新授权。

## 仅后台应用授权

后台任务无需启用浏览器登录。配置 Spring Security registration 使用 `client_credentials` 和 Provider 的 `token-uri`，注入 `QuickOAuth2Client` 后通过稳定的 registration ID 获取应用授权引用：

```java
AuthorizedClientRef ref = quickOAuth2Client.application("service");
RestClient api = quickOAuth2Client.restClient(ref,
    RestClient.builder().baseUrl("https://api.example.com"));
```

只需后台 API 调用时，将 `moli.oauth.client.enabled` 设为 `true`，保持 `login.enabled` 关闭即可。此模式不需要 Web 安全链或 `OAuth2LoginHandler`。

## 存储选择

默认长期授权存储为进程内存，重启后授权会丢失，适合开发或单实例场景。多实例或生产环境可按需添加存储模块：

```yaml
moli:
  oauth:
    client:
      authorized-client:
        store: redis # 或 jdbc
      authorization-state:
        store: redis # 临时授权状态可独立选择；默认 session
      redis:
        key-prefix: my-app:oauth2
```

- 使用 Redis：添加 `moli-quick-oauth-client-redis`，由宿主提供 `StringRedisTemplate` 和 Redis 连接配置。
- 使用 JDBC：添加 `moli-quick-oauth-client-jdbc`，由宿主提供 `DataSource`，并手工执行模块 JAR 中的 `schema-mysql.sql` 或 `schema-postgresql.sql`。
- 使用 Redis 临时授权状态时，生产环境保持 `cookie-secure: true`，并为共享 Redis 的不同应用或环境设置不同的 `key-prefix`。

令牌数据属于敏感信息；存储编码不等于加密。请限制数据库和 Redis 访问，并使用受保护的连接。不要将客户端密钥、access token 或 refresh token 提交到仓库。

## 完整参考

包含完整配置项、存储和刷新行为、渠道扩展、宿主 Session / JWT 接入、安全注意事项及集成测试说明的文档见 [OAuth 模块完整使用说明](../README.md)。

## 渠道与子模块 README 导航

[QQ、微信、Google 完整配置与协议差异](../CHANNELS.md)。OAuth Client 父模块只聚合构建，不作为运行时依赖；业务选择 Starter，再增加实际渠道/存储。子模块指南如下。

| 模块 | 用途 |
| --- | --- |
| [moli-quick-oauth-client-core](moli-quick-oauth-client-core/README.md) | 原生类型、非 Web 授权、身份/渠道接口 |
| [moli-quick-oauth-client-spring-boot-autoconfigure](moli-quick-oauth-client-spring-boot-autoconfigure/README.md) | 公共自动配置、Web configurer |
| [moli-quick-oauth-client-spring-boot-starter](moli-quick-oauth-client-spring-boot-starter/README.md) | 业务便捷入口，默认关闭 |
| [moli-quick-oauth-client-github](moli-quick-oauth-client-github/README.md) | GitHub.com OAuth App |
| [moli-quick-oauth-client-qq](moli-quick-oauth-client-qq/README.md) | QQ 互联网站登录 |
| [moli-quick-oauth-client-wechat](moli-quick-oauth-client-wechat/README.md) | 微信网站扫码及公众号网页授权 |
| [moli-quick-oauth-client-google](moli-quick-oauth-client-google/README.md) | Google OIDC 登录 |
| [moli-quick-oauth-client-jdbc](moli-quick-oauth-client-jdbc/README.md) | 长期授权 JDBC 存储 |
| [moli-quick-oauth-client-redis](moli-quick-oauth-client-redis/README.md) | 临时状态/长期授权/分布式刷新 |
