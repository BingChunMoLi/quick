# moli-quick-redis-spring-boot-starter

提供 Redis 对象序列化配置和 RedisUtil，连接配置沿用 Spring Boot。

[项目总览](../README.md)

当前源码：Java 17，Spring Boot 依赖管理 4.1.0，Quick 版本 `1.0.13-bate`。依赖示例使用项目总览中的统一版本管理；工具和聚合模块的特殊接入方式见下文。

## 引入与连接

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-redis-spring-boot-starter</artifactId>
</dependency>
```


```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password: ${REDIS_PASSWORD:}
      database: 0
```

连接及默认模板优先沿用 Boot。`RedisUtil` 按名称使用 `redisTemplate`，兼容 Boot 的 `RedisTemplate<Object,Object>` 和宿主提供的模板，多模板场景不按泛型猜测候选。

已有 Boot/宿主 `redisTemplate` 时不会改写其序列化设置；Boot 默认对象模板仍使用 JDK 序列化。只有存在唯一连接工厂候选且缺少该名称的模板时，Quick 才创建默认对象模板，名称为 `redisTemplate`（兼容别名 `createRedisTemplate`），key/hash key 使用字符串，value/hash value 使用 Jackson 2 JSON。缺少连接工厂时不创建模板或 RedisUtil。

## 使用

```java
import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import java.util.concurrent.TimeUnit;

// 通过构造器注入 RedisUtil redis
redis.setObject("catalog:item:1", "book", 5, TimeUnit.MINUTES);
String value = redis.getObject("catalog:item:1");
boolean acquired = redis.setObjectIfAbsent("job:once", "owner", 30, TimeUnit.SECONDS);
redis.deleteObject("catalog:item:1");
```

还提供 list/set/hash 的读写接口，见 [RedisUtil](src/main/java/com/bingchunmoli/autoconfigure/redis/util/RedisUtil.java)。

## 定制与边界

`redisTemplate` 名称的现有 Bean 会使默认模板退让；`RedisUtil` 可通过同类型 Bean 替换。Quick 的 JSON 序列化器包含 Java 类型信息，与纯字符串/普通 JSON 缓存不应混用，只读取受控 Redis 数据。更换序列化协议前应验证已有缓存的兼容性。

默认序列化器 Bean 名为 `redisSerializer`（兼容别名 `getRedisSerializer`），Quick 创建模板时通过参数注入该 Bean，宿主可提供同名 RedisSerializer 替换。保留 Boot 模板时，如需使用 JSON，应显式提供名为 `redisTemplate` 的模板并设置此序列化器；仅替换序列化器 Bean 不会修改已有模板。

本模块提供同步 Redis 工具，依赖 Boot 同步 Redis Starter；需要 ReactiveRedisTemplate 的应用应另外引入 reactive Starter。本模块不提供分布式锁或跨多个命令的业务事务。`keys(pattern)` 对应全量模式检索，不适合生产大键空间扫描。
