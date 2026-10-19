# moli-quick-oauth-resource-server-spring-boot-autoconfigure

JWT/opaque 验证、配置器和默认 API 安全链。

[父模块完整指南](../README.md) · [独立示例](../../examples/README.md)

Java 17 / Spring Boot 4.1.0，Quick `1.0.13-bate`。先在宿主导入 Quick BOM，再按需添加：

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-resource-server-spring-boot-autoconfigure</artifactId>
</dependency>
```

前缀 moli.oauth.resource-server，默认关闭。QuickResourceServerWebSupport 组合原生 Bearer Filter 和认证 Provider，保留宿主 CSRF/Session 策略；Quick 默认链单独采用无状态 API 策略。原生资源连接配置继续使用 spring.security.oauth2.resourceserver。
