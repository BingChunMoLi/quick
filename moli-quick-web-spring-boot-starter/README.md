# moli-quick-web-spring-boot-starter

可复用的 Spring MVC 基础 Starter，提供统一错误响应、参数校验处理和请求链路 ID。

## 引入依赖

```xml
<dependency>
    <groupId>com.bingchunmoli</groupId>
    <artifactId>moli-quick-web-spring-boot-starter</artifactId>
</dependency>
```

## 统一异常响应

Starter 默认使用项目的 `ResultVO`：

```json
{
  "code": "A0400",
  "msg": "请求参数校验失败",
  "data": {
    "timestamp": "2026-08-01T09:00:00Z",
    "path": "/orders",
    "requestId": "8dc5a0e0d5b4457ca2d9238d22d713af",
    "details": {
      "orderNo": "订单号不能为空"
    }
  }
}
```

内置处理包括：

- `@Valid`、`@Validated` 参数校验和绑定错误；
- JSON 解析错误、缺少参数、参数类型错误；
- 404、405 和其他 Spring `ErrorResponseException`；
- 未处理异常，默认隐藏内部异常消息；
- `ApiException` 自定义 HTTP 状态、业务码和详情；
- 引入幂等或限流 Starter 时，自动映射 HTTP 409 和 HTTP 429。

业务异常示例：

```java
throw new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "A1234",
        "订单状态不允许当前操作",
        Map.of("orderStatus", order.getStatus()));
```

声明自己的 `WebExceptionResponseFactory` Bean 可以替换整个错误响应协议；也可以通过
`moli.web.exception-handling.enabled=false` 关闭默认异常处理。

## 请求 ID

每个请求都会获得请求 ID，并写入：

- 响应头 `X-Request-Id`；
- Servlet 请求属性 `moli.web.request-id`；
- SLF4J MDC 的 `requestId` 字段。

安全格式的客户端请求 ID 会被透传；包含空白、控制字符、超长等不安全值会被替换。
声明自定义 `RequestIdGenerator` Bean 可替换默认的无连字符 UUID。

Logback 示例：

```xml
<pattern>%d %-5level [%X{requestId}] %logger - %msg%n</pattern>
```

## 配置项

```yaml
moli:
  web:
    enabled: true
    request-id:
      enabled: true
      header-name: X-Request-Id
      mdc-key: requestId
      accept-incoming: true
      max-length: 128
    exception-handling:
      enabled: true
      include-exception-message: false
      validation-error-code: A0400
      malformed-json-code: A0427
      system-error-code: B0001
      duplicate-request-code: A0506
      rate-limit-code: A0501
```

Starter 不会默认配置 CORS、打印请求体或自动包装正常响应，这些行为通常涉及安全策略或既有接口协议，
应由业务应用显式决定。
