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

需要可用的 `RedisConnectionFactory`。未出现名为 `redisTemplate` 的 Bean 时，Quick 的 `createRedisTemplate` 工厂创建对象模板；该模板的 key/hash key 使用字符串，value/hash value 使用 Jackson 2 JSON，并注册 JavaTime/Jdk8 等模块。已有 Boot/宿主 `redisTemplate` 时会退让，不会自动改写其序列化设置。`RedisUtil` 使用容器提供的 RedisTemplate。

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

`redisTemplate` 名称的现有 Bean 会使默认模板退让；`RedisUtil` 可通过同类型 Bean 替换。序列化内容包含 Java 类型信息，与纯字符串/普通 JSON 缓存不应混用，只读取受控 Redis 数据。更换序列化协议前应验证已有缓存的兼容性。

自动配置内部直接调用默认序列化器工厂；需要完全替换 value 编码时，显式提供自己的 `redisTemplate`。本模块不提供分布式锁或跨多个命令的业务事务。`keys(pattern)` 对应全量模式检索，不适合生产大键空间扫描。
