# Microsoft 登录

Microsoft identity platform v2 网页 OIDC 登录，支持个人微软账号及 Microsoft Entra 工作/学校账号。

[项目总览](../../../README.md) · [公共接入](../../README.md) · [渠道导航](../README.md)

## 依赖

先导入 Quick BOM，再引入公共 Starter 和渠道模块，版本保持一致：

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-client-spring-boot-starter</artifactId>
</dependency>
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-client-microsoft</artifactId>
</dependency>
```

宿主提供 Servlet 环境、SecurityFilterChain 和 OAuth2LoginHandler，并应用 QuickOAuth2WebSupport.configurer()，具体代码见公共接入文档。

## 配置

```yaml
moli:
  oauth:
    client:
      enabled: true
      login:
        enabled: true
      microsoft:
        tenant: common
        offline: true
        # prompt: select_account
        # allowed-tenants:
        #   - 11111111-2222-3333-4444-555555555555
spring:
  security:
    oauth2:
      client:
        registration:
          microsoft:
            provider: microsoft
            client-id: ${MICROSOFT_CLIENT_ID}
            client-secret: ${MICROSOFT_CLIENT_SECRET}
```

在 Microsoft Entra 应用注册中配置 Web 平台、与 tenant 匹配的受众类型，并登记精确的回调地址。默认入口为 /oauth2/authorization/microsoft，回调为 {baseUrl}/login/oauth2/code/microsoft。

| tenant | 接受的账号 |
| --- | --- |
| common（默认） | 任意 Entra 租户和个人微软账号 |
| organizations | 工作/学校账号 |
| consumers | 个人微软账号 |
| 目录 GUID | 指定目录的成员和来宾 |

allowed-tenants 可额外限定目录 GUID。个人账号对应目录 9188040d-6c67-4c5b-b112-36a304b66dad。域名租户别名、主权云和 Entra External ID/B2C 不属于此预设；当前端点面向 Azure 公有云。

默认 scope 为 openid,profile,email；offline=true 在默认 scope 中加入 offline_access。显式 registration.scope 优先，需要刷新时自行保留 offline_access。prompt 为可选授权参数。

多个 registration 可指定 provider: microsoft；自定义注册仓库使用 moli.oauth.client.provider-mappings 映射。所有 registration 共用本渠道的 tenant/allowed-tenants 策略。普通接入无需填写 issuer-uri；默认静态端点避免启动时 discovery。显式原生 issuer-uri 仍采用 Spring discovery，common 模板应使用上述 tenant 配置。

## 校验与身份

登录使用授权码、S256 PKCE、state 和原生 OIDC nonce/audience/有效期校验。多租户场景额外要求 tid 为 GUID、issuer 与目录一致，并验证 JWKS 中签名密钥的 issuer 范围。密钥仅从配置的 JWK 地址获取，缓存并支持轮换，不根据令牌任意选择网络地址。

归一化 provider=microsoft、subject=<issuer>|<sub>。原生 OidcUser 的 name 同样包含 issuer，避免不同目录的相同 sub 共用长期授权记录；原生 ID Token 保留。name/email 可缺失或变化，邮箱只是联系资料，不作为账户绑定或租户权限依据。宿主应按 provider + subject 关联本地账号，并明确处理来宾账号策略。

## 验证

本地签名 OIDC 测试覆盖个人/组织账号、目录限制、签名密钥 issuer、nonce、audience、过期及错误签名。真实接入还需使用自己的应用凭证完成登录、拒绝授权和刷新冒烟。

官方参考：[OIDC 与账号受众](https://learn.microsoft.com/en-us/entra/identity-platform/v2-protocols-oidc)、[租户及签名密钥 issuer 校验](https://learn.microsoft.com/en-us/entra/identity-platform/access-tokens#validate-the-issuer)。
