# 第三方登录渠道

渠道模块按需添加，与公共 Starter 配合使用；版本由 `moli-quick-dependencies` 管理。GitHub 见 [公共接入](README.md)，Microsoft 和 Apple 的完整示例见下方专门指南。

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-client-spring-boot-starter</artifactId>
</dependency>
<!-- 按需选择以下渠道 -->
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-client-qq</artifactId>
</dependency>
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-client-wechat</artifactId>
</dependency>
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-client-google</artifactId>
</dependency>
```

宿主提供 Servlet 环境、`SecurityFilterChain` 和 `OAuth2LoginHandler`，应用 `http.with(quickOAuth2WebSupport.configurer(), Customizer.withDefaults())`。完整示例及 Session/JWT 处理器见 [公共接入说明](README.md)。Quick 不自动注册本地用户，不签发业务 JWT，不把第三方 Authentication 保存成业务 Session。

## 配置与入口

```yaml
moli:
  oauth:
    client:
      enabled: true
      login:
        enabled: true
      google:
        offline: true    # 按需请求 refresh token；仅登录时可省略
        # prompt: consent # 需要重新征求同意时使用，避免每次登录强制弹窗
spring:
  security:
    oauth2:
      client:
        registration:
          qq:
            provider: qq
            client-id: ${QQ_APP_ID}
            client-secret: ${QQ_APP_KEY}
          wechat:
            provider: wechat
            client-id: ${WECHAT_OPEN_APP_ID}
            client-secret: ${WECHAT_OPEN_APP_SECRET}
          wechat-mp:
            provider: wechat-mp
            client-id: ${WECHAT_MP_APP_ID}
            client-secret: ${WECHAT_MP_APP_SECRET}
            # 默认 snsapi_userinfo；仅需标识时显式配置 scope: snsapi_base
          google:
            provider: google
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
```

只保留已引入模块和已申请凭证的 registration，避免缺少环境变量导致启动失败。

| 场景 | provider | 默认 scope | 授权入口 | 回调路径 |
| --- | --- | --- | --- | --- |
| QQ 互联网站登录 | `qq` | `get_user_info` | `/oauth2/authorization/qq` | `/login/oauth2/code/qq` |
| 微信开放平台网站扫码 | `wechat` | `snsapi_login` | `/oauth2/authorization/wechat` | `/login/oauth2/code/wechat` |
| 微信内公众号网页授权 | `wechat-mp` | `snsapi_userinfo` | `/oauth2/authorization/wechat-mp` | `/login/oauth2/code/wechat-mp` |
| Google OIDC | `google` | `openid,profile,email` | `/oauth2/authorization/google` | `/login/oauth2/code/google` |
| Microsoft 个人/组织账号 | `microsoft` | `openid,profile,email` | `/oauth2/authorization/microsoft` | `/login/oauth2/code/microsoft` |
| Apple 网站登录 | `apple` | 内部 `openid,name,email`，发给 Apple 为 `name email` | `/oauth2/authorization/apple` | `/login/oauth2/code/apple`（POST） |

回调完整地址为 `{baseUrl}` 加回调路径，需在各平台登记公共 HTTPS 地址/授权回调域名。多应用可使用其他 registrationId，只要指定上表 provider；入口和回调中的末段同步变为该 registrationId。显式 `registration/provider` 配置覆盖渠道默认值。宿主自定义仓库可通过 `moli.oauth.client.provider-mappings.<registrationId>` 关联适配器。

## QQ

使用 QQ 互联的网站应用 AppID/AppKey。授权 scope 使用 QQ 的逗号分隔格式；Token 与 OpenID 请求显式指定 `fmt=json`，不执行 JSONP。取得 OpenID 后检查返回的 client_id 与当前应用一致，再读取用户信息。

归一化 `provider=qq`，`subject=<AppID>|<OpenID>`，原生 OAuth2User 名称也是该值。显示昵称和 QQ 头像；不把昵称或头像当作账号标识。OpenID 与 AppID 绑定，不能直接用于跨应用自动合并账户。

支持 QQ 的 GET 授权码交换、refresh token 轮换；已失效/过期的 refresh token 错误映射为 `invalid_grant`，清理授权后重新登录。接口没有明确返回刷新令牌到期时间时不自行推算，存储策略仍按公共规则处理。

附加配置 `moli.oauth.client.qq.open-id-uri` 默认 `https://graph.qq.com/oauth2.0/me`，通常无需修改。其余端点使用原生 provider 配置。

## 微信

同一模块提供两个适配器：`wechat` 使用开放平台网站应用的 `/connect/qrconnect`，`wechat-mp` 使用公众号的 `/connect/oauth2/authorize`。公众号页面需要在微信客户端打开，并具有相应网页授权权限；网站应用与公众号使用各自的 AppID/Secret。

授权 URL 使用 `appid` 和 `#wechat_redirect`；授权码/刷新请求使用微信规定的 GET 参数和独立刷新端点。`snsapi_base` 只创建基于 OpenID 的身份，不调用用户信息接口，名称和头像可以为空；`snsapi_userinfo` 和 `snsapi_login` 读取用户资料，并校验资料中的 OpenID 与 Token 响应一致。公众号 scope 必须是 `snsapi_base` 或 `snsapi_userinfo` 其中一个；网站扫码只允许 `snsapi_login`，不符合时启动失败。

两个适配器均归一化为 `provider=wechat`、`subject=<AppID>|<OpenID>`，原生用户名称同样包含 AppID。`unionid` 在平台返回时保留在属性中，缺失时不推测，也不自动从 OpenID 切换为 UnionID 作为主标识。宿主可在确认开放平台归属后自行建立跨应用 UnionID 绑定，避免不同登录时资料变化造成账户分裂。本模块不实现小程序 `code2Session`、移动 SDK 凭证登录或公众号全局 `client_credential` Token。

附加选项：

```yaml
moli:
  oauth:
    client:
      wechat:
        language: zh_CN
        refresh-token-uri: https://api.weixin.qq.com/sns/oauth2/refresh_token
```

替换网站适配器时提供名为 `weChatOAuth2ProviderAdapter` 的 Bean；公众号适配器对应 `weChatMpOAuth2ProviderAdapter`。Token 客户端和用户服务也可使用公共扩展点替换。

## Google

使用 Google Cloud 的 Web application OAuth client，配置同意屏幕、测试用户/发布状态和精确回调 URI。默认静态端点包括授权、Token、UserInfo、JWK 和 Google issuer，默认配置启动时无需下载 discovery；首次校验 ID Token 时读取并缓存 JWK。显式配置原生 `issuer-uri` 时遵循 Spring 的 discovery 行为，采用发现的端点和客户端认证方式；单独显式配置的端点或客户端认证方式仍可覆盖对应元数据，渠道默认值不会覆盖 discovery。

Google 使用 S256 PKCE 和原生 OIDC nonce/签名/issuer/audience/有效期校验，不能删除 `openid` scope。归一化 `provider=google`、`subject=<issuer>|<sub>`；原生 `OidcUser` 和 ID Token 原样交给宿主，邮箱只在 `email_verified=true` 时放入归一化 email 字段。不要使用邮箱自动合并本地账户，也不要只根据授权页的域名提示实施 Workspace 访问控制。

`moli.oauth.client.google.offline=true` 添加 `access_type=offline`；Google 不保证每次授权都返回新的 refresh token，`prompt=consent` 仅在确有重新授权需求时配置。同一 registration 和原生用户再次登录时，未返回 refresh token 会保留已保存的刷新令牌；返回同一个令牌但未返回有效期时保留原有效期，返回新令牌时以新响应为准。不同用户之间不继承刷新令牌；需要彻底清除授权时调用 `QuickOAuth2Client.remove(ref)`。本模块的 Google 渠道面向用户授权码/OIDC 登录，不实现 service account，也不支持该渠道的 `client_credentials`。

## Microsoft 与 Apple

[Microsoft 接入指南](moli-quick-oauth-client/moli-quick-oauth-client-microsoft/README.md) 包含 common/organizations/consumers/目录 GUID、可选租户白名单、PKCE 和离线授权；使用 issuer + sub 区分目录身份，并校验签名密钥 issuer。

[Apple 接入指南](moli-quick-oauth-client/moli-quick-oauth-client-apple/README.md) 包含 Services ID、Team/Key ID、P-256 私钥动态签名或静态 client-secret JWT、首次姓名和隐藏邮箱处理。Apple 使用 form_post：默认 Session 状态需要 SameSite=None + Secure；Redis 状态 Cookie 自动按此方式设置。只豁免有效待处理回调的 CSRF Token，业务 POST 保持宿主防护。

## API、存储与调试

各渠道继续使用公共 `QuickOAuth2Client.authorize(ref)`、内存/JDBC/Redis 存储和刷新协调器。Google/Microsoft 的标准 Bearer API 可使用公共 `restClient`。QQ/微信的许多 API 要求 query 参数中的 access_token、AppID/OpenID；公共 Bearer 拦截器不会将其自动转换，宿主应在服务端取得授权后按对应 API 规则调用，不向浏览器返回 Token。

QQ/微信协议要求的 GET 请求包含敏感参数，默认异常只保留脱敏说明；不要在生产中开启记录完整 URI 的 HTTP/代理调试日志。无需为这些渠道关闭 CSRF 或全局改变 Session 策略。

自动测试使用本地 HTTP OAuth 服务和签名的模拟 ID Token，不需要真实账号。手工冒烟依次执行：

1. 各平台注册应用，登记回调并设置环境变量，按公共示例启动宿主。
2. 分别访问上表入口，确认平台同意/拒绝授权后，宿主成功回调或失败处理器收到结果。
3. 微信网站在桌面浏览器扫码，公众号链接在微信内打开；分别验证 userinfo 和 base，后者允许无昵称头像。
4. 校验归一化身份、业务 Session/JWT，以及重放回调失败；Token 保持服务端保存。
5. Google 检查原生 OidcUser；需要后台 API 时验证离线授权和刷新。QQ/微信检查实际平台权限及 refresh token 轮换。

官方协议参考：[QQ 授权与刷新](https://wiki.connect.qq.com/使用authorization_code获取access_token)、[QQ OpenID](https://wiki.connect.qq.com/获取用户openid_oauth2-0)、[QQ 返回码](https://wiki.connect.qq.com/公共返回码说明)、[微信网站登录](https://developers.weixin.qq.com/doc/oplatform/Website_App/WeChat_Login/Wechat_Login.html)、[微信公众号网页授权](https://developers.weixin.qq.com/doc/offiaccount/OA_Web_Apps/Wechat_webpage_authorization.html)、[Google OIDC](https://developers.google.com/identity/openid-connect/openid-connect)。
