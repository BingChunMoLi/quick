# moli-quick-idempotency-spring-boot-starter

基于注解的 Spring Boot 方法幂等 Starter，支持单机内存、Redis 和自定义存储实现。

## 引入依赖

```xml
<dependency>
    <groupId>com.bingchunmoli</groupId>
    <artifactId>moli-quick-idempotency-spring-boot-starter</artifactId>
</dependency>
```

## 使用

```java
@Idempotent(
        key = "#p0.orderNo",
        namespace = "order:create",
        timeout = 10,
        timeUnit = TimeUnit.MINUTES)
public Order createOrder(CreateOrderRequest request) {
    return orderService.create(request);
}
```

同一个幂等 Key 在有效期内再次调用会抛出 `DuplicateRequestException`。调用成功后 Key
保留到 TTL 到期；调用抛出异常时默认释放 Key，允许业务重试。`CompletionStage`
异步失败同样会释放 Key。

参数别名 `#p0`、`#a0` 始终可用；编译时保留参数名后也可以使用 `#request`。
不配置 `key` 时，将使用方法签名和参数 JSON 的 SHA-256 摘要。

## 存储配置

### 内存模式

默认使用内存实现，适合单实例服务、本地开发和测试：

```yaml
moli:
  idempotency:
    store: memory
    default-timeout: 10m
    key-prefix: moli:idempotency
```

### Redis 模式

集群或多实例部署应使用 Redis：

```yaml
moli:
  idempotency:
    store: redis
    default-timeout: 10m
```

Redis 模式要求应用中存在 `StringRedisTemplate`。抢占使用带 TTL 的 `SET NX`，释放时
通过 Lua 比较所有权 Token 后删除，避免删除已经被其他请求重新获得的 Key。

## 自定义实现

声明自己的 `IdempotencyStore` 或 `IdempotencyKeyResolver` Bean，自动配置会退让：

```java
@Bean
IdempotencyStore databaseIdempotencyStore() {
    return new DatabaseIdempotencyStore();
}
```

## 配置项

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `moli.idempotency.enabled` | `true` | 是否启用自动配置 |
| `moli.idempotency.store` | `memory` | `memory` 或 `redis` |
| `moli.idempotency.default-timeout` | `10m` | 注解未指定时的 Key 有效期 |
| `moli.idempotency.key-prefix` | `moli:idempotency` | 存储 Key 前缀 |
| `moli.idempotency.order` | `-2147483548` | AOP 执行顺序 |

目前直接支持同步返回值和 `CompletionStage`。Reactor `Mono`/`Flux` 等惰性流需要独立的
订阅生命周期适配，不应直接套用同步切面。
