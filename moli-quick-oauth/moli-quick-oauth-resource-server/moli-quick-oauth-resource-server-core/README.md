# moli-quick-oauth-resource-server-core

scope/role 权限映射内核。

[父模块完整指南](../README.md) · [独立示例](../../examples/README.md)

Java 17 / Spring Boot 4.1.0，Quick `1.0.13-bate`。先在宿主导入 Quick BOM，再按需添加：

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-resource-server-core</artifactId>
</dependency>
```

ClaimAuthorities 仅从明确配置的 scope/role 字段产生权限，支持字符串和字符串集合。未知结构不提升权限。无 Boot 自动配置及 Servlet API 直接依赖。
