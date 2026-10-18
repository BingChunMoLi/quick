# moli-quick-oauth-client-spring-boot-starter

业务应用的 OAuth Client 便捷入口，聚合 Core 和公共自动配置；渠道、数据库、Redis、Web 服务器按需提供。

[项目总览](../../../README.md) · [Client 导航](../README.md) · [完整接入](../../README.md)

当前源码基线为 Java 17 / Spring Boot 4.1.0，Quick `1.0.13-bate`。先按项目总览导入 Quick BOM，再添加所需组件。

## 引入与开启

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-client-spring-boot-starter</artifactId>
</dependency>
```

仅需后台 `client_credentials` 调用时：

```yaml
moli:
  oauth:
    client:
      enabled: true
spring:
  security:
    oauth2:
      client:
        registration:
          service:
            provider: business-idp
            client-id: ${OAUTH_CLIENT_ID}
            client-secret: ${OAUTH_CLIENT_SECRET}
            authorization-grant-type: client_credentials
        provider:
          business-idp:
            token-uri: https://id.example.test/oauth/token
```

通过构造器注入 `QuickOAuth2Client`，调用 `quick.authorize(quick.application("service"))`。无需打开 login.enabled。

## 浏览器登录

添加所需渠道依赖，并设置 `moli.oauth.client.login.enabled=true`。宿主提供 Servlet/MVC、OAuth2LoginHandler 和自己的安全链：

```java
http.with(quickOAuth2WebSupport.configurer(), Customizer.withDefaults());
```

默认入口为 `/oauth2/authorization/{registrationId}`，回调为 `/login/oauth2/code/{registrationId}`。自定义回调路径时同时修改原生 registration.redirect-uri。完整可运行宿主代码、Session/JWT 回调示例见 [完整接入](../../README.md)。

## 模块选择

- [GitHub](../moli-quick-oauth-client-github/README.md)、[QQ](../moli-quick-oauth-client-qq/README.md)、[微信](../moli-quick-oauth-client-wechat/README.md)、[Google](../moli-quick-oauth-client-google/README.md)：按实际平台选择。
- [JDBC](../moli-quick-oauth-client-jdbc/README.md)、[Redis](../moli-quick-oauth-client-redis/README.md)：长期授权持久化及共享刷新协调。
- 纯原生 OAuth2/OIDC Provider 可直接设置端点/issuer，不必创建渠道模块。

默认 memory 存储仅适合单进程/开发。Starter 不对外暴露 Token API，也不自动桥接 Quick JWT。平台实际权限、回调登记和凭证申请由宿主完成。
