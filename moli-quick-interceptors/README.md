# moli-quick-interceptors

Spring MVC 请求签名校验，配套 JSON 重复读取过滤器及 Redis nonce 防重放。

[项目总览](../README.md)

当前源码：Java 17，Spring Boot 依赖管理 4.1.0，Quick 版本 `1.0.13-bate`。依赖示例使用项目总览中的统一版本管理；工具和聚合模块的特殊接入方式见下文。

## 引入与启用

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-interceptors</artifactId>
</dependency>
```

该模块已依赖 Quick Redis Starter。应用需要 `RedisUtil`，以及 Jackson 2 的 `com.fasterxml.jackson.databind.ObjectMapper` Bean；Boot 4 宿主若仅配置 Jackson 3，需显式提供兼容的 Jackson 2 Mapper。

只有 `moli.interceptor.sign.sign.enable=true` 才注册验签工具、拦截器及 `CacheFilter`。

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
moli:
  interceptor:
    sign:
      algorithm: SHA256WithRSA
      public-key: ${SIGN_PUBLIC_KEY}
      private-key: ${SIGN_PRIVATE_KEY}
      sign:
        enable: true
        name: sign
        parameter-position: HEAD
      nonce:
        enable: true
        name: nonce
        parameter-position: HEAD
      timestamp:
        enable: true
        name: t
        parameter-position: HEAD
      sign-valid-time: 2000
      nonce-redis-prefix: "my-app:sign:nonce:"
      ignore-parameters-list:
        - file
      ignore-path-list:
        - /captcha
```

配置中的算法名称与实现选择大小写一致：`SHA256WithRSA`（默认）、`MD5WithRSA`、`MD5`。参数位置支持 `HEAD/QUERY/BODY/ALL`，ALL 按头、查询、请求体顺序探测。时间戳及有效期单位为毫秒。

## RSA 密钥与调用

公钥使用 X.509、私钥使用 PKCS#8 的 Base64 内容，不包含 PEM 的 BEGIN/END 行。生成示例：

```java
var generator = java.security.KeyPairGenerator.getInstance("RSA");
generator.initialize(2048);
var pair = generator.generateKeyPair();
String publicKey = java.util.Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
String privateKey = java.util.Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
```

对外接入时发送方负责签名、接收方负责验签；字段排序、拼接、编码必须与服务端一致。应直接核对 [AbstractSignUtil](src/main/java/com/bingchunmoli/util/AbstractSignUtil.java) 和 [RSAAbstractSignUtil](src/main/java/com/bingchunmoli/util/RSAAbstractSignUtil.java)，不要只根据算法名拼接待签名文本。服务端只验签时不应向客户端分发私钥。

## JSON 与防重放

`CacheFilter` 在进入 MVC 前缓存 `application/json` 或 `application/*+json`，保证验签和 Controller 均能完整读取；默认上限 1 MiB，超限返回 413。手动注册 `SignInterceptor` 时也要先注册此过滤器，并让它先于其他读取请求体的过滤器执行。

nonce 在签名验证成功后通过 Redis 带 TTL 的 SET NX 原子消费；错误签名不占用 nonce，同一 nonce 在有效期内只允许一个请求成功。`getSignParam()` 不消费 nonce。自定义 `AbstractSignUtil.verify()` 时，完成签名计算后应调用 `completeVerification(parameters, signatureValid)`。

## 边界与定制

该模块校验请求签名，不代替 HTTPS、用户认证或业务授权。默认拦截范围为全部 MVC 路径，忽略列表应由宿主明确设置。验签开关关闭时不会注册上述签名组件。

浏览器协议演示位于 [Vue 示例](src/main/vue/quick-interceptors-demo/README.md)。对新协议优先使用默认 SHA256WithRSA；MD5 相关实现仅用于既有协议兼容。
