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

签名有效期必须大于 0。启用时间戳时，仅接受 `(服务端当前时间 - sign-valid-time, 服务端当前时间]` 内的值，未来时间和恰好到期的时间戳均被拒绝；发送方应同步时钟，不能使用未来时间延长有效期。签名计算验证完成后会再次检查时效，再原子消费 nonce。nonce 从成功验证时起保存一个完整有效期，覆盖签名的剩余可接受时间。

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

签名串按小写 HTTP 方法、空格、大小写不变的请求 URI、换行、参数串构成。参数先按原始名称排序，再对每个名称和值各转义一次：`% → %25`、`& → %26`、`= → %3D`、回车 `→ %0D`、换行 `→ %0A`，之后用 `=` 连接键值、`&` 连接参数。其余字符（包括中文、空格和 JSON 标点）保持原样。这是签名输入的编码，不能直接替代 URL 编码或修改实际 JSON 正文。Java 辅助方法为 `SignatureCanonicalizer.encodeComponent`，Vue 示例已采用相同规则，两端共用回归样例。

例如，`title=invoice%26transfer%3Dtrue`（线上 URL）签入的参数为 `title=invoice%26transfer%3Dtrue`，表示一个包含 `&transfer=true` 的 title 值；两个独立参数则签为 `title=invoice&transfer=true`。字面量 `%26` 会签为 `%2526`，不会与真实 `&` 混淆。迁移时，包含这些特殊字符的调用方必须同步升级签名端与服务端，不会回退到旧的歧义拼接；不含这些字符的原有签名保持兼容。

查询和表单中的每个参数只允许一个值，包括被忽略的字段；重复参数返回 400，不再静默取首值。批量数据可通过签入完整正文的 JSON 数组传递。

## JSON 与防重放

`CacheFilter` 在进入 MVC 前缓存 `application/json` 或 `application/*+json`，保证验签和 Controller 均能完整读取；默认上限 1 MiB，超限返回 413。手动注册 `SignInterceptor` 时也要先注册此过滤器，并让它先于其他读取请求体的过滤器执行。

受保护请求的正文只支持 JSON 和 POST `application/x-www-form-urlencoded` 表单字段。带正文的纯文本、XML、二进制、multipart、缺少 Content-Type 的请求，以及其他方法的表单正文返回 400，避免在未签入正文的情况下放行。无正文的查询请求仍可使用。文件和二进制上传需要另行定义包含文件摘要的签名协议，不能直接沿用此协议；`ignore-parameters-list` 中的 `file` 仅指普通参数名，不表示允许未验签的文件上传。

使用 `BODY` 或 `ALL` 回退到 JSON 读取签名时，签名字段必须是字符串，且该名称在整份 JSON 中只能出现一次。构造待签名文本时，将该字段的值替换为空字符串 `""`，保留字段本身以及其他字符、顺序、空白和数值写法；正文仍按现有协议去掉实际 CR/LF 换行，再作为 `body` 值参与上述转义。Java 可使用 `SignatureCanonicalizer.jsonBodyForSigning(json, "sign")`。例如先对 `{"amount":1,"sign":""}` 计算签名，再将结果写入同一 JSON 的 `sign` 字符串，服务端仅在计算签名输入时置空，Controller 收到的仍是完整原文。不要删除该字段或重新排序其他字段。签名取自请求头或查询参数时，JSON 正文维持原签名规则，其中名为 `sign` 的业务字段也不会被置空；Vue 示例使用请求头签名，无需改成正文签名。

从 JSON 提取签名、时间戳或 nonce 时，非法 JSON、重复字段、多个根值或同名元数据在多处出现均返回 400，不再把解析失败作为服务端 500。

`authorization` 是认证头的保留签名字段，不能作为查询或表单参数；JSON 请求中的 `body` 是真实正文的保留签名字段，不能同时作为查询参数。保留名检查不区分大小写，冲突请求返回 400。普通非 JSON 请求仍可使用名为 `body` 的业务参数。

启用的签名、时间戳、nonce 字段名必须彼此不同，不能使用 `body` 或 `authorization`；这些必要字段也不能列入 `ignore-parameters-list`。冲突配置会报配置错误，避免正文或防重放字段被覆盖、删除。

同一个启用的元数据字段不能同时由请求头和查询／表单提供，即使值相同也返回 400；配置为 `HEAD` 的字段不能再作为查询／表单参数。配置为 JSON `BODY` 的字段也不能同时出现在请求头或查询中。这样 Controller 可见的同名参数不会被元数据覆盖后从签名输入中消失。`ALL` 仍按头、查询、正文选择单一来源；头或查询传递元数据时，JSON 中的同名业务字段仍作为完整正文的一部分签入。

nonce 在签名验证成功后通过 Redis 带 TTL 的 SET NX 原子消费；错误签名不占用 nonce，同一 nonce 在有效期内只允许一个请求成功。`getSignParam()` 不消费 nonce。自定义 `AbstractSignUtil.verify()` 时，完成签名计算后应调用 `completeVerification(parameters, signatureValid)`。

客户端 RSA 签名 Base64、长度或格式错误返回 400；格式合法但验签失败返回 401。公钥或算法配置错误仍作为服务端配置异常报告，不会伪装成客户端签名错误。

## 边界与定制

该模块校验请求签名，不代替 HTTPS、用户认证或业务授权。默认拦截范围为全部 MVC 路径，忽略列表应由宿主明确设置。验签开关关闭时不会注册上述签名组件。

浏览器协议演示位于 [Vue 示例](src/main/vue/quick-interceptors-demo/README.md)。对新协议优先使用默认 SHA256WithRSA；MD5 相关实现仅用于既有协议兼容。
