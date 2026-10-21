# moli-quick-web-spring-boot-starter

[项目总览](../README.md)

当前源码版本 `1.0.13-bate`，Java 17 / Spring Boot 依赖管理 4.1.0；下方省略版本的依赖示例需先导入项目总览中的 Quick BOM。


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

Spring MVC 的 `ErrorResponse` 异常保留原 HTTP 状态及协议响应头：不支持的 Content-Type 返回 415，方法不允许返回 405 并保留 `Allow`，框架错误中的 `Retry-After` 等头也会保留。响应体继续使用配置的 `WebExceptionResponseFactory`，工厂自身的其他响应头不丢失。缺少路径变量等服务端映射错误、返回值校验失败保持 500；入参校验仍为 400。

普通业务异常上的 `@ResponseStatus` 也保留声明的 HTTP 状态，支持继承、组合注解与属性别名，并沿 cause 链优先使用最外层的状态声明。错误体仍由响应工厂生成，使用配置的客户端／系统错误码；`reason` 支持宿主 MessageSource 本地化，未声明 reason 时使用标准 HTTP 状态描述，不暴露异常内部消息。Controller 自身的异常处理器和优先级更高的宿主 Advice 继续优先执行。

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

过滤器覆盖 Servlet REQUEST 和 ASYNC 分派，异步重新分派复用首次验证或生成的 ID。每次分派进入时绑定 MDC，退出时恢复该线程原有上下文，成功响应和异步异常响应均适用。独立后台任务或 Callable 业务工作线程的 MDC 传播由宿主执行器的任务装饰器等机制管理。

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

## 宿主集成与边界

本模块面向 Servlet Spring MVC，不是 WebFlux 错误处理器。`ApiException`、`WebExceptionResponseFactory` 位于 `com.bingchunmoli.web.exception`；请求 ID 配置见 [QuickWebProperties](src/main/java/com/bingchunmoli/web/autoconfigure/QuickWebProperties.java)。

自定义正常响应继续由 Controller 返回；已有 ControllerAdvice 与本模块的优先级需明确协调。客户端请求 ID 是跟踪信息，不作为身份或授权依据。默认隐藏未处理异常的内部消息，需要调试时显式启用 include-exception-message。

引入 [幂等](../moli-quick-idempotency-spring-boot-starter/README.md) 或 [限流](../moli-quick-rate-limit-spring-boot-starter/README.md) 时分别适配 409/429；仅引入 Web Starter 不会启用这些方法切面。
