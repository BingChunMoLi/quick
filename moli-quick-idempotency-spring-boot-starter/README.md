# moli-quick-idempotency-spring-boot-starter

[项目总览](../README.md)

当前源码版本 `1.0.13-bate`，Java 17 / Spring Boot 依赖管理 4.1.0；下方省略版本的依赖示例需先导入项目总览中的 Quick BOM。


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

## 代理调用与接入边界

注解类型为 `com.bingchunmoli.idempotency.annotation.Idempotent`，需作用于 Spring 管理的可代理方法；同类内部调用不经过切面。内存存储不跨实例共享；Redis 模式由宿主提供 StringRedisTemplate（例如使用 Boot Redis Starter）。默认不缓存或重放上一次业务响应，重复请求抛出异常，由业务或 [Web Starter](../moli-quick-web-spring-boot-starter/README.md) 转为响应。

注解可以声明在实现方法或接口方法上，接口继承和泛型桥接方法在 JDK/CGLIB 代理下均支持；实现方法的显式配置优先于接口配置。自动配置使用同一套注解查找规则筛选并执行 Advisor，没有注解的方法不会获得幂等保护。手动装配代理时，将 `IdempotencyAspect` 作为 Advisor 通过 `addAdvisor` 注册；自动配置接入方式及配置项不变。

也支持子类引入接口、业务实现继承自未实现该接口的父类。接口方法按其解析后的实际实现方法精确匹配，未标注的同名重载不会获得保护。

Advisor 持有独立、稳定的 Advice 实例，支持通过 Spring `Advised.removeAdvisor(advisor)` 移除；按 Advice 移除时使用 `Advised.removeAdvice(advisor.getAdvice())`。

与限流组合时，默认限流切面顺序在幂等之前，重复请求也可能计入限额；可按业务调整 order。幂等 TTL 需覆盖业务执行时间，TTL 到期后可能接受新请求，不提供数据库事务的恰好一次提交保证。
