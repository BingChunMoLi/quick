# moli-quick-oauth-client-spring-boot-autoconfigure

绑定 Quick/原生 OAuth2 配置，提供公共授权客户端、存储回退、Servlet 登录配置器与启动校验。

[项目总览](../../../README.md) · [Client 导航](../README.md) · [完整接入](../../README.md)

当前源码基线为 Java 17 / Spring Boot 4.1.0，Quick `1.0.13-bate`。先按项目总览导入 Quick BOM，再添加所需组件。

## 接入定位

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-client-spring-boot-autoconfigure</artifactId>
</dependency>
```

常规应用优先直接引入 [Starter](../moli-quick-oauth-client-spring-boot-starter/README.md)。此模块对 Servlet 依赖为 optional；纯后台调用可以在非 Web 容器使用。

## 开关

```yaml
moli:
  oauth:
    client:
      enabled: true
      login:
        enabled: false
      authorization-state:
        store: session
        ttl: 10m
      authorized-client:
        store: memory
      refresh:
        coordination: auto
        wait-timeout: 15s
        lease: 30s
```

默认总开关和 login.enabled 均为 false。客户端 ID、密钥、provider 端点沿用 `spring.security.oauth2.client.registration/provider`。显式 issuer 使用原生 discovery，单独指定的端点/认证方式可覆盖元数据；没有 issuer 时才应用渠道端点默认值。

## 覆盖与宿主集成

宿主可替换原生 ClientRegistrationRepository、OAuth2AuthorizedClientService、OAuth2AuthorizedClientManager、Provider/RefreshCoordinator Bean。授权码、刷新、应用授权的原生泛型 Token 客户端 Bean 可分别覆盖默认实现，优先于渠道适配器。

Web 用户服务使用 Bean 名 `quickOAuth2UserService`、`quickOidcUserService`；失败处理器名为 `quickOAuth2FailureHandler`。登录启用后必须提供 OAuth2LoginHandler 并将 Quick configurer 应用到宿主 SecurityFilterChain，否则启动失败。非 Servlet 应用不能启用浏览器登录。

OIDC 渠道可以实现 OidcProviderAdapter 定制 ID Token decoder 和原生 OIDC 用户服务；未定制时使用 Spring 默认行为。宿主提供 JwtDecoderFactory<ClientRegistration> Bean 时公共 decoder 工厂退让，宿主需保留相应渠道的 issuer、签名、audience、有效期与租户校验。普通 OAuth2ProviderAdapter 的既有方法保持兼容；新增带 callback 参数的 identity 重载仅在认证成功后提供浏览器展示资料，不将这些字段作为身份凭证。

支持受 state/浏览器/回调路径约束的 form_post CSRF 豁免。已有宿主 CSRF 配置继续生效；不会全局关闭或重新启用 CSRF。Apple 的 Session/Redis Cookie 配置见对应渠道指南。

Quick 启用时仅排除 Boot 原生 OAuth2 Web 自动配置，避免其创建意外的 OAuth 登录安全链；Quick 不创建业务链，也不改变宿主整体 CSRF/Session 策略。关闭 Quick 不干预 Boot 原生行为。

## 存储与限制

选择 JDBC/Redis 但缺少模块或连接 Bean 会失败，不静默退回内存。临时状态与长期 Token 独立选型。回调 Authentication 只用于当前请求，业务登录态由宿主建立；同一授权重复登录的刷新令牌保留规则见 [渠道说明](../../CHANNELS.md)。
