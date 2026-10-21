# moli-quick-rate-limit-spring-boot-starter

[项目总览](../README.md)

当前源码版本 `1.0.13-bate`，Java 17 / Spring Boot 依赖管理 4.1.0；下方省略版本的依赖示例需先导入项目总览中的 Quick BOM。


基于注解的 Spring Boot 方法限流 Starter，提供固定窗口算法，支持单机内存、Redis 和自定义存储实现。

## 引入依赖

```xml
<dependency>
    <groupId>com.bingchunmoli</groupId>
    <artifactId>moli-quick-rate-limit-spring-boot-starter</artifactId>
</dependency>
```

## 使用

```java
@RateLimit(
        key = "#userId",
        namespace = "order:create",
        permits = 20,
        window = 1,
        timeUnit = TimeUnit.MINUTES,
        message = "操作过于频繁，请稍后重试")
public Order createOrder(Long userId, CreateOrderRequest request) {
    return orderService.create(request);
}
```

示例表示每个用户一分钟最多创建 20 次订单。超过限制时抛出
`RateLimitExceededException`，异常中包含完整 Key、窗口限额和建议等待时间。

参数别名 `#p0`、`#a0` 始终可用；编译时保留参数名后也可以使用 `#userId`。
不配置 `key` 时，同一方法的所有调用共享一个全局限流桶。

## 存储配置

### 内存模式

默认使用内存实现，适合单实例服务、本地开发和测试：

```yaml
moli:
  rate-limit:
    store: memory
    default-permits: 100
    default-window: 1m
    key-prefix: moli:rate-limit
```

### Redis 模式

集群或多实例部署应使用 Redis：

```yaml
moli:
  rate-limit:
    store: redis
    default-permits: 100
    default-window: 1m
```

Redis 模式要求应用中存在 `StringRedisTemplate`。计数、TTL 初始化和限流判定在一段 Lua
脚本中原子完成；达到上限后的请求不会继续增加计数。

## 自定义实现

声明自己的 `RateLimitStore` 或 `RateLimitKeyResolver` Bean，自动配置会退让：

```java
@Bean
RateLimitStore databaseRateLimitStore() {
    return new DatabaseRateLimitStore();
}
```

可通过自定义 Store 接入令牌桶、滑动窗口或其他限流算法。

## 配置项

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `moli.rate-limit.enabled` | `true` | 是否启用自动配置 |
| `moli.rate-limit.store` | `memory` | `memory` 或 `redis` |
| `moli.rate-limit.default-permits` | `100` | 注解未指定时的窗口请求上限 |
| `moli.rate-limit.default-window` | `1m` | 注解未指定时的窗口长度 |
| `moli.rate-limit.key-prefix` | `moli:rate-limit` | 存储 Key 前缀 |
| `moli.rate-limit.order` | `-2147483598` | AOP 执行顺序 |

当前内置算法是固定窗口。它实现简单、开销低，但窗口边界附近可能出现短时流量突增；需要更平滑的
限流效果时，可以提供自定义 `RateLimitStore` 实现令牌桶或滑动窗口。

## 代理调用与接入边界

注解类型为 `com.bingchunmoli.ratelimit.annotation.RateLimit`，需经过 Spring 代理；同类内部调用不生效。Redis 模式由宿主提供 StringRedisTemplate，内存模式不在多个实例之间共享限额。

注解可以声明在实现方法或接口方法上，支持继承接口及泛型桥接方法，兼容 JDK/CGLIB 代理；实现方法的显式配置优先。自动配置通过 Advisor 使用一致的匹配和执行规则，未注解方法不受限流。手动装配代理时，将 `RateLimitAspect` 通过 `addAdvisor` 注册；自动配置及原有执行顺序不变。

也支持子类引入接口、业务实现继承自未实现该接口的父类。接口方法按其解析后的实际实现方法精确匹配，未标注的同名重载不会受限流。

Advisor 持有独立、稳定的 Advice 实例，支持通过 Spring `Advised.removeAdvisor(advisor)` 移除；按 Advice 移除时使用 `Advised.removeAdvice(advisor.getAdvice())`。

将示例 key 写为 `#p0` 可避免依赖编译器参数名保留配置。限流仅限制方法调用，不自动按登录用户/IP 分组；分组维度由 key 明确指定。与幂等同时使用时默认限流先执行，重复请求也可能消耗窗口额度。HTTP 429 映射由 [Web Starter](../moli-quick-web-spring-boot-starter/README.md) 或宿主异常处理器负责。
