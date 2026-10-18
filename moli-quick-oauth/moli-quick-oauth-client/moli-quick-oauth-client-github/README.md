# moli-quick-oauth-client-github

GitHub.com OAuth App 渠道扩展，可与其他渠道和存储模块组合。

[项目总览](../../../README.md) · [Client 导航](../README.md) · [完整接入](../../README.md)

当前源码基线为 Java 17 / Spring Boot 4.1.0，Quick `1.0.13-bate`。先按项目总览导入 Quick BOM，再添加所需组件。

## 引入与配置

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-client-github</artifactId>
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
      github:
        fetch-email: false
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            provider: github
            client-id: ${OAUTH_CLIENT_ID}
            client-secret: ${OAUTH_CLIENT_SECRET}
```

默认 scope 为 `read:user`；可通过原生 registration.scope 显式配置。宿主必须提供处理器并应用 Quick configurer，见 [Starter](../moli-quick-oauth-client-spring-boot-starter/README.md)。

## 登录与身份

入口 `/oauth2/authorization/github`，默认回调 `{baseUrl}/login/oauth2/code/github`；在平台登记对应地址。使用其他 registrationId 时两处末段同步变更。

使用 GitHub 数字用户 ID 作为稳定 subject，默认启用 S256 PKCE。fetch-email=true 时默认 scope 增加 user:email，并选择经过验证的邮箱；显式 scope 不被覆盖。兼容逗号 scope、长期令牌及带访问/刷新有效期的令牌响应。

## 扩展与边界

GitHub App installation token、device flow 和通用 client_credentials 不在本渠道范围内。邮箱与用户名不能代替数字 ID 作为账户绑定主键。

自定义原生注册仓库通过 moli.oauth.client.provider-mappings 关联渠道。Token 客户端和用户服务支持公共 Bean 扩展，宿主负责本地账号绑定和最终 Session/JWT 响应。CI 使用本地模拟平台，真实账号冒烟步骤见 [平台指南](../../README.md)；不要向浏览器返回第三方 Token。
