# moli-quick-oauth-auth-server-spring-boot-autoconfigure

授权服务器的属性、密钥和安全链接入。

[父模块完整指南](../README.md) · [独立示例](../../examples/README.md)

Java 17 / Spring Boot 4.1.0，Quick `1.0.13-bate`。先在宿主导入 Quick BOM，再按需添加：

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-oauth-auth-server-spring-boot-autoconfigure</artifactId>
</dependency>
```

前缀 moli.oauth.auth-server，默认关闭。核心类型 QuickAuthorizationServerWebSupport 在 web 包；Settings、注册客户端、TokenGenerator、JWKSource 均支持原生 Bean 覆盖。只有显式开启才替换 Boot 的默认授权配置/临时密钥及默认用户自动配置；表单登录必须由宿主提供认证实现。
