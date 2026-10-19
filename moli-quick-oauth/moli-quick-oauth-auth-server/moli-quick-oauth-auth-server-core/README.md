# moli-quick-oauth-auth-server-core

原生授权服务扩展、并发协调和受限快照编解码。

[父模块完整指南](../README.md) · [独立示例](../../examples/README.md)

Java 17 / Spring Boot 4.1.0，Quick `1.0.13-bate`。先在宿主导入 Quick BOM，再按需添加：

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-auth-server-core</artifactId>
</dependency>
```

公开 GrantCoordinator、CoordinatingGrantProvider、AuthorizationAttributeCodec、AuthorizationSnapshotCodec 与带清理的内存实现。默认属性 codec 使用 Spring Security 的类型白名单，预先验证可读回，拒绝未擦除凭证；自定义主体通过显式 codec 扩展。无 Boot 自动配置和 Servlet API 直接依赖。

凭证检查覆盖嵌套 Map、集合、对象数组，以及 Authentication 的 principal/details 和 OAuth2 主体属性。读取与写入均拒绝未擦除的认证凭证、UserDetails 密码和循环引用；共享但无循环的已擦除主体仍可正常保存。
