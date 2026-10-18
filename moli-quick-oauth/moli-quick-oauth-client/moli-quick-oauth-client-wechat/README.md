# moli-quick-oauth-client-wechat

同时提供微信开放平台网站扫码和微信内公众号网页授权两个适配器，不包含小程序 code2Session。

[项目总览](../../../README.md) · [Client 导航](../README.md) · [完整接入](../../README.md)

当前源码基线为 Java 17 / Spring Boot 4.1.0，Quick `1.0.13-bate`。先按项目总览导入 Quick BOM，再添加所需组件。

## 引入

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-client-wechat</artifactId>
</dependency>
```

同时需要公共 Starter。宿主成功处理器和安全链接入见上级完整指南。

## 配置

```yaml
moli:
  oauth:
    client:
      enabled: true
      login:
        enabled: true
      wechat:
        language: zh_CN
spring:
  security:
    oauth2:
      client:
        registration:
          wechat:
            provider: wechat
            client-id: ${WECHAT_OPEN_APP_ID}
            client-secret: ${WECHAT_OPEN_APP_SECRET}
          wechat-mp:
            provider: wechat-mp
            client-id: ${WECHAT_MP_APP_ID}
            client-secret: ${WECHAT_MP_APP_SECRET}
            scope: snsapi_userinfo
```

只保留实际使用的 registration。网站应用与公众号使用各自的凭证，不能用公众号 AppID 发起网站扫码。

| provider | 场景 | scope | 入口 |
| --- | --- | --- | --- |
| `wechat` | 网站扫码 | 仅 `snsapi_login` | /oauth2/authorization/wechat |
| `wechat-mp` | 微信内公众号网页 | `snsapi_userinfo` 或 `snsapi_base`，二选一 | /oauth2/authorization/wechat-mp |

默认回调为 `{baseUrl}/login/oauth2/code/{registrationId}`。公众号授权页面应在微信内打开，并配置有权限的授权回调域名；扫码对应开放平台的网站应用。

## 身份与协议

授权请求转换为 appid 参数并添加 #wechat_redirect，state 使用随机十六进制文本并保留 Spring 校验。Token/refresh 使用微信规定的 GET 参数；refresh-token-uri 可单独配置，默认 https://api.weixin.qq.com/sns/oauth2/refresh_token。

两个适配器均归一化为 provider=wechat、subject=<AppID>|<OpenID>。`snsapi_base` 不调用用户资料接口，名称/头像允许为空；userinfo/login scope 拉取资料并核对 OpenID。平台返回的 unionid 保留为属性，不自动切换账户标识或跨应用合并。

## 定制与边界

替换适配器时使用 Bean 名 `weChatOAuth2ProviderAdapter` / `weChatMpOAuth2ProviderAdapter`。仍可使用公共 Token 客户端和用户服务扩展。QQ/微信的许多 API 需要 query 中的 Token/OpenID，公共 Bearer RestClient 不自动转换这些协议。

不处理移动 SDK 登录、公众号全局 client_credential Token 或小程序会话。协议差异、身份绑定与平台冒烟步骤见 [微信/QQ/Google 指南](../../CHANNELS.md)。
