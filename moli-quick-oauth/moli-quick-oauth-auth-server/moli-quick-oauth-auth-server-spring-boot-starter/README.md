# moli-quick-oauth-auth-server-spring-boot-starter

授权服务器应用依赖入口。

[父模块完整指南](../README.md) · [独立示例](../../examples/README.md)

Java 17 / Spring Boot 4.1.0，Quick `1.0.13-bate`。先在宿主导入 Quick BOM，再按需添加：

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-auth-server-spring-boot-starter</artifactId>
</dependency>
```

复用 Core/AutoConfigure，不引入 Web 服务器、数据库或 Redis。浏览器流程需要宿主 Servlet/MVC 和用户认证；纯 client_credentials 不必启用 login.enabled。完整默认行为、示例和定制见父模块文档。
