moli-quick-redis-spring-boot-starter：RedisSerializerAutoConfiguration and RedisUtil  
moli-quick-mybatis-plus-spring-boot-starter：MybatisPlusConfigAutoConfiguration and CommonMetaHandler  
moli-common-beans：common ResultVO and ProfileEnum  
# moli-quick-spring-boot-starter
1. 简介

为spring-boot提供快速开始功能集成,推荐环境(jdk17,springboot3)
2. 功能介绍

| 模块名称                                        | 起始版本       | 主要功能                            | 注入bean                                                                                                                           |
|---------------------------------------------|------------|---------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| common-beans                                | 1.0.0-bate | 公共bean类,例如ResultVO、ProfileEnum  | 无                                                                                                                                |
| moli-quick-aop-boot-starter                 | 1.0.8-bate | AOP日志和执行时间计算注解                  | LogAspect、ExecutionAspect                                                                                                        |
| moli-quick-mybatis-plus-spring-boot-starter | 1.0.0-bate | MybatisPLus自动配置,包括分页插件,全局放删表插件等 | CommonMetaHandler,MybatisPlusInterceptor,PaginationInnerInterceptorPaginationInnerInterceptor,MybatisPlusConfigAutoConfiguration |
| moli-quick-redis-spring-boot-starter        | 1.0.0-bate | Redis序列化的自动配置及RedisUtil的注入      | RedisTemplate<String, Object>,RedisUtil,RedisSerializer<Object>,RedisSerializerAutoConfiguration,RedisUtilAutoConfiguration      |
| moli-quick-interceptors                     | 1.0.9-bate | SHA256WithRSA签名                 | CacheFilter,InterceptorsRegistrar,SignInterceptor,SignUtil,SignAutoConfiguration,InterceptorsAutoConfiguration                   |
| moli-quick-object-storage-*                 | 1.0.13-bate | OSS、OBS、COS、S3 单独或多 Provider 对象存储 | ObjectStorageClient、ObjectStorageClientRegistry 及所选厂商 Client                                                           |
| moli-quick-idempotency-spring-boot-starter  | 1.0.13-bate | 注解式接口幂等，支持内存、Redis 和自定义存储 | IdempotencyAspect、IdempotencyStore、IdempotencyKeyResolver                                                                  |
| moli-quick-rate-limit-spring-boot-starter   | 1.0.13-bate | 注解式方法限流，支持内存、Redis 和自定义存储 | RateLimitAspect、RateLimitStore、RateLimitKeyResolver                                                                        |
| moli-quick-web-spring-boot-starter          | 1.0.13-bate | 统一异常响应、参数校验、请求 ID 与 MDC       | GlobalWebExceptionHandler、RequestIdGenerator、RequestIdFilter                                                               |

3. 快速开始(添加如下依赖开箱即用)
## 父POM版本管理
```xml
<dependencyManagement>
    <dependency>
        <groupId>com.bingchunmoli</groupId>
        <artifactId>moli-quick-spring-boot-starter</artifactId>
        <version>LATEST</version>
        <type>pom</type>
        <scope>import</scope>
    </dependency>
</dependencyManagement>
```
## common-beans
```xml
<dependency>
    <artifactId>moli-common-beans</artifactId>
    <groupId>com.bingchunmoli</groupId>
    <version>LATEST</version>
</dependency>
```
## moli-quick-aop-boot-starter
```xml
<dependency>
    <artifactId>moli-quick-aop-boot-starter</artifactId>
    <groupId>com.bingchunmoli</groupId>
    <version>LATEST</version>
</dependency>
```
## moli-quick-mybatis-plus-spring-boot-starter
```xml
<dependency>
    <artifactId>moli-quick-mybatis-plus-spring-boot-starter</artifactId>
    <groupId>com.bingchunmoli</groupId>
    <version>LATEST</version>
</dependency>
```
## moli-quick-redis-spring-boot-starter
```xml
<dependency>
    <artifactId>moli-quick-redis-spring-boot-starter</artifactId>
    <groupId>com.bingchunmoli</groupId>
    <version>LATEST</version>
</dependency>
```

## moli-quick-interceptors

```xml

<dependency>
    <artifactId>moli-quick-interceptors</artifactId>
    <groupId>com.bingchunmoli</groupId>
    <version>LATEST</version>
</dependency>
```

## moli-quick-object-storage

按需选择一个 Provider（`oss`、`obs`、`cos` 或 `s3`），业务代码统一注入
`ObjectStorageClient`：

```xml
<dependency>
    <artifactId>moli-quick-object-storage-s3</artifactId>
    <groupId>com.bingchunmoli</groupId>
    <version>LATEST</version>
</dependency>
```

详细配置与使用方式见 [对象存储模块文档](moli-quick-object-storage/README.md)。

## moli-quick-idempotency-spring-boot-starter

```xml
<dependency>
    <artifactId>moli-quick-idempotency-spring-boot-starter</artifactId>
    <groupId>com.bingchunmoli</groupId>
    <version>LATEST</version>
</dependency>
```

提供 `@Idempotent` 方法幂等，支持内存、Redis 和自定义 `IdempotencyStore`，详见
[幂等 Starter 文档](moli-quick-idempotency-spring-boot-starter/README.md)。

## moli-quick-rate-limit-spring-boot-starter

```xml
<dependency>
    <artifactId>moli-quick-rate-limit-spring-boot-starter</artifactId>
    <groupId>com.bingchunmoli</groupId>
    <version>LATEST</version>
</dependency>
```

提供 `@RateLimit` 固定窗口方法限流，支持内存、Redis 和自定义 `RateLimitStore`，详见
[限流 Starter 文档](moli-quick-rate-limit-spring-boot-starter/README.md)。

## moli-quick-web-spring-boot-starter

```xml
<dependency>
    <artifactId>moli-quick-web-spring-boot-starter</artifactId>
    <groupId>com.bingchunmoli</groupId>
    <version>LATEST</version>
</dependency>
```

提供统一错误响应、参数校验处理、请求 ID/MDC，并自动适配幂等冲突和限流异常，详见
[Web Starter 文档](moli-quick-web-spring-boot-starter/README.md)。
