# Apple 登录

Sign in with Apple 网页授权码登录，支持原生 OIDC 校验、POST 回调、服务端生成 client secret 和刷新令牌。

[项目总览](../../../README.md) · [公共接入](../../README.md) · [渠道导航](../README.md)

## 依赖与平台设置

导入 Quick BOM 后引入公共 Starter 与渠道模块：

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-client-spring-boot-starter</artifactId>
</dependency>
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-client-apple</artifactId>
</dependency>
```

在 Apple Developer 配置启用 Sign in with Apple 的主 App ID、网站 Services ID、域名/Return URL，并生成相应 .p8 私钥。Web client-id 使用 Services ID。真实回调需为已登记的 HTTPS 域名地址，不使用 localhost、IP 或 URL fragment。

宿主提供 Servlet 环境、SecurityFilterChain 和 OAuth2LoginHandler，并应用 QuickOAuth2WebSupport.configurer()。授权入口为 /oauth2/authorization/apple，默认回调为 {baseUrl}/login/oauth2/code/apple。

## 动态签名配置

```yaml
moli:
  oauth:
    client:
      enabled: true
      login:
        enabled: true
      apple:
        team-id: ${APPLE_TEAM_ID}
        key-id: ${APPLE_KEY_ID}
        private-key: ${APPLE_PRIVATE_KEY_LOCATION} # 例如 file:/run/secrets/AuthKey.p8
        client-secret-ttl: 5m
spring:
  security:
    oauth2:
      client:
        registration:
          apple:
            provider: apple
            client-id: ${APPLE_SERVICES_ID}
server:
  servlet:
    session:
      cookie:
        secure: true
        same-site: none
```

私钥为 P-256、PKCS#8 PEM，由宿主以 Spring Resource 提供；不提交 .p8 文件或令牌到仓库。每次换取/刷新令牌时生成短期 ES256 client secret，包含 Team ID、Key ID、client-id 和 Apple audience，不修改共享注册仓库或存储客户端密钥。TTL 为 1 到 15777000 的整数秒，默认 5 分钟；密钥在进程中缓存，换钥后重启/滚动更新实例。

也支持外部生成的 JWT：不配置 apple 下的 team-id/key-id/private-key，改为配置 spring.security.oauth2.client.registration.apple.client-secret。应用检查 JWT 结构、受众、client-id 和有效期；签名真实性由 Apple Token 端点验证。静态 JWT 需由宿主在过期前更新，推荐使用动态签名。

## POST 回调、Cookie 和 CSRF

默认 scope 为 openid,name,email。openid 用于 Spring 内部选择 OIDC 流程；发给 Apple 的 scope 只包含 name/email，response_mode 固定为 form_post。

默认临时状态存储为 session。跨站 POST 需要浏览器携带原授权 Session，上例配置 SameSite=None + Secure，并要求 HTTPS。若业务 Session 要保持原 Cookie 策略，可使用公共 Redis 临时状态：引入 moli-quick-oauth-client-redis、提供 StringRedisTemplate，设置 authorization-state.store: redis。Redis 仓库仅对 form_post 的独立状态 Cookie 设置 SameSite=None + Secure，其他渠道保持 Lax；cookie-secure=false 时拒绝 form_post。

Quick 只对回调路径下、能够从当前浏览器加载待处理授权且 state/redirect path 匹配的 form POST 忽略 CSRF Token；重放、未知 state、其他业务 POST 仍受保护。无需全局关闭 CSRF；宿主原本关闭 CSRF 时不会被重新启用。自定义授权状态仓库也必须验证浏览器绑定，不能仅凭请求参数 state 查找公共记录。

## 身份与首次资料

state、签名、issuer、audience、有效期和 nonce 校验通过后，归一化 provider=apple、subject=<issuer>|<sub>。邮箱只来自已验证 ID Token 且 email_verified=true 时提供，支持 Apple 隐藏邮箱；回调 user 字段里的 email 不作为身份依据。

Apple 只在首次同意时返回 user.name。模块限制该字段大小并拒绝含控制字符/尖括号的姓名片段，将结果作为可选 name 交给宿主；它是浏览器提交的展示资料，使用时仍应按普通用户输入处理并在页面转义。宿主首次绑定时自行保存，后续缺失时不要清空已有昵称。邮箱或昵称不用于自动合并账号。

本模块实现网站登录，不包含原生移动端凭证交换、应用迁移、远端撤销或 Apple 服务端通知。移除本地授权仍使用公共 QuickOAuth2Client.remove(ref)，不代表撤销 Apple 端授权。

## 验证与参考

测试使用本地 HTTP 服务、真实 RSA ID Token 签名及 P-256 client secret 签名，覆盖 POST 回调、刷新、无效令牌、state/浏览器绑定、重放和 CSRF。真实凭证及 Apple 平台联调由宿主环境完成。

官方参考：[Apple 授权与首次 user 字段](https://developer.apple.com/documentation/signinwithapplerestapi/request-an-authorization-to-the-sign-in-with-apple-server.?changes=_6%2C_6)、[Token 交换](https://developer.apple.com/documentation/signinwithapplerestapi/generate-and-validate-tokens)。
