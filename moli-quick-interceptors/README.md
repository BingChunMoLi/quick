## 快速开始:

JSON 验签由 `CacheFilter` 在进入 MVC 前缓存请求体，验签和 Controller 可以分别完整读取，支持
`application/json` 和 `application/*+json`。默认最多缓存 1 MiB，超限返回 HTTP 413；手动注册
`SignInterceptor` 时也必须在 MVC 前注册 `CacheFilter`，并在其他读取请求体的过滤器之前执行。

启用 nonce 后，只有签名验证成功才使用 Redis 带 TTL 的 `SET NX` 原子抢占；同一 nonce
在有效期内只允许一个请求通过。调用 `getSignParam()` 只提取参数，不消耗 nonce；错误签名
不会占用 nonce，Redis 抢占失败也不会放行业务请求。

自定义签名算法继承 `AbstractSignUtil` 并覆盖 `verify()` 时，应在完成签名计算后调用
`completeVerification(parameters, signatureValid)`，复用同样的 nonce 抢占逻辑。

1. 添加依赖
```xml
<dependency>
    <groupId>com.bingchunmoli</groupId>
    <artifactId>moli-quick-redis-spring-boot-starter</artifactId>
</dependency>
```
2. 添加配置
```yml
moli:
  interceptor:
    sign:
      public-key: rsaPublicKey
      private-key: rsaPriavateKey
      #签名字段必须开启
      sign:
        enable: true
        name: sign
```
> key可用如下代码生成
```java
KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
generator.initialize(1024);
generator.generateKeyPair();
```
获取base64编码后的密钥
```java
new String(Base64.getEncoder().encode(keyPair.getPrivate().getEncoded()));
```

### 全部配置
```yml
moli:
  interceptor:
    sign:
      #最大1024KeySize,不含有---begin ---end字符
      public-key:
      private-key:
      #签名字段必须开启
      sign:
        enable: true
        #字段的name
        name: sign
        #从何处获取
        parameter-position: head
      #可选开启，但推荐开启
      nonce:
        enable: true
        name: nonce
        parameter-position: head
      timestamp:
        enable: true
        name: t
        parameter-position: head
      #忽略字段名称(默认有file)
      ignore-parameters-list:
        - file
      #不需要签名的path
      ignore-path-list:
        - /captcha
      #签名算法
      algorithm: SHA256withRSA
      #签名有效期（单位毫秒)
      sign-valid-time: 2000
      #nonce存放redis的前缀
      nonce-redis-prefix: "moli:sign:nonce:"
```
