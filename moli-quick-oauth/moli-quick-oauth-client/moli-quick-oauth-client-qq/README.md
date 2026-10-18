# moli-quick-oauth-client-qq

QQ 互联网站应用 渠道扩展，可与其他渠道和存储模块组合。

[项目总览](../../../README.md) · [Client 导航](../README.md) · [完整接入](../../README.md)

当前源码基线为 Java 17 / Spring Boot 4.1.0，Quick `1.0.13-bate`。先按项目总览导入 Quick BOM，再添加所需组件。

## 引入与配置

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-client-qq</artifactId>
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
spring:
  security:
    oauth2:
      client:
        registration:
          qq:
            provider: qq
            client-id: ${OAUTH_CLIENT_ID}
            client-secret: ${OAUTH_CLIENT_SECRET}
```

默认 scope 为 `get_user_info`；可通过原生 registration.scope 显式配置。宿主必须提供处理器并应用 Quick configurer，见 [Starter](../moli-quick-oauth-client-spring-boot-starter/README.md)。

## 登录与身份

入口 `/oauth2/authorization/qq`，默认回调 `{baseUrl}/login/oauth2/code/qq`；在平台登记对应地址。使用其他 registrationId 时两处末段同步变更。

使用 QQ 的 GET Token/refresh 接口，显式请求 fmt=json；读取 OpenID 后检查 client_id 与当前应用相符，再取用户资料。归一化 provider=qq、subject=<AppID>|<OpenID>，原生用户名称同样包含 AppID；默认映射昵称和 QQ 头像。

## 扩展与边界

OpenID 不能直接跨 AppID 合并账号。附加端点 moli.oauth.client.qq.open-id-uri 可配置，默认 https://graph.qq.com/oauth2.0/me。令牌失效码会按规则映射 invalid_grant；不使用 JSONP 执行。

自定义原生注册仓库通过 moli.oauth.client.provider-mappings 关联渠道。Token 客户端和用户服务支持公共 Bean 扩展，宿主负责本地账号绑定和最终 Session/JWT 响应。CI 使用本地模拟平台，真实账号冒烟步骤见 [平台指南](../../CHANNELS.md)；不要向浏览器返回第三方 Token。
