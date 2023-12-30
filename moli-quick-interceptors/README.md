## 快速开始:

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