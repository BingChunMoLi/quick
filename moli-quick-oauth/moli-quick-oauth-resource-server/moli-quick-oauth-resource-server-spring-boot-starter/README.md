# moli-quick-oauth-resource-server-spring-boot-starter

资源服务器应用依赖入口。

[父模块完整指南](../README.md) · [独立示例](../../examples/README.md)

Java 17 / Spring Boot 4.1.0，Quick `1.0.13-bate`。先在宿主导入 Quick BOM，再按需添加：

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-resource-server-spring-boot-starter</artifactId>
</dependency>
```

复用 Core/AutoConfigure，不引入 Web 服务器、数据库、Redis 或 security-util。JWT 默认要求 issuer、audiences；opaque 要求 introspection URI 与凭证，宿主自定义 Bean 可覆盖默认实现。
