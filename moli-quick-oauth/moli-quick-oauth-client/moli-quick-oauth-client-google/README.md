# moli-quick-oauth-client-google

Google Web application OIDC 渠道扩展，可与其他渠道和存储模块组合。

[项目总览](../../../README.md) · [Client 导航](../README.md) · [完整接入](../../README.md)

当前源码基线为 Java 17 / Spring Boot 4.1.0，Quick `1.0.13-bate`。先按项目总览导入 Quick BOM，再添加所需组件。

## 引入与配置

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-client-google</artifactId>
</dependency>
```

同时需要公共 Starter；同一渠道的多个 registration 指向同一 provider。

```yaml
moli:
  oauth:
    client:
      enabled: true
      login:
        enabled: true
      google:
        offline: true
        # prompt: consent
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            provider: google
            client-id: ${OAUTH_CLIENT_ID}
            client-secret: ${OAUTH_CLIENT_SECRET}
```

默认 scope 为 `openid,profile,email`；可通过原生 registration.scope 显式配置。宿主必须提供处理器并应用 Quick configurer，见 [Starter](../moli-quick-oauth-client-spring-boot-starter/README.md)。

## 登录与身份

入口 `/oauth2/authorization/google`，默认回调 `{baseUrl}/login/oauth2/code/google`；在平台登记对应地址。使用其他 registrationId 时两处末段同步变更。

使用 S256 PKCE 和原生 OIDC nonce、签名、issuer、audience、有效期校验；登录处理器保留原生 OidcUser/ID Token。subject=<issuer>|<sub>，归一化 email 仅在 email_verified=true 时提供。显式配置 issuer-uri 时采用 discovery，默认静态端点不会覆盖其元数据。

## 扩展与边界

offline=true 添加 access_type=offline；Google 不保证每次都返回刷新令牌，同一授权再次登录会按公共规则保留旧刷新令牌。openid scope 不可删除。本渠道不实现 service account 或 client_credentials。

自定义原生注册仓库通过 moli.oauth.client.provider-mappings 关联渠道。Token 客户端和用户服务支持公共 Bean 扩展，宿主负责本地账号绑定和最终 Session/JWT 响应。CI 使用本地模拟平台，真实账号冒烟步骤见 [平台指南](../../CHANNELS.md)；不要向浏览器返回第三方 Token。
