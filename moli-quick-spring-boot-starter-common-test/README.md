# moli-quick-spring-boot-starter-common-test

仓库中的 Spring Boot 演示和回归应用，组合 Redis、AOP、签名拦截器与公共模型；不是业务项目需要引入的测试 Starter。

[项目总览](../README.md)

当前源码：Java 17，Spring Boot 依赖管理 4.1.0，Quick 版本 `1.0.13-bate`。依赖示例使用项目总览中的统一版本管理；工具和聚合模块的特殊接入方式见下文。

## 运行和测试

在 IDE 中运行 [TestApplication](src/main/java/com/bingchunmoli/test/TestApplication.java)，按需要配置 Redis 和签名参数。该应用声明 `@EnableAop`、`@EnableAsync`，连接项参照 [Redis 模块](../moli-quick-redis-spring-boot-starter/README.md)。

```shell
mvn -pl :moli-quick-spring-boot-starter-common-test -am -Dgpg.skip=true test
```

此命令从根工程执行；应用 POM 没有独立可执行 JAR 的 repackage 声明，普通 jar 不能直接当作 Boot fat jar 启动。

## 演示接口

| 接口 | 用途 |
| --- | --- |
| 根路径 | 日志/耗时切面演示，返回 test |
| GET/POST `/hello` | 读取简单参数 str |
| POST `/hello2` | 读取 JSON 对象中的 str |

现有演示异常处理会返回异常消息，控制器包含演示用 CORS 配置；这些配置服务于仓库演示，不应直接复制为生产安全策略。Logback 示例可能在 `api/logs` 写日志，测试产物不作为源码提交。

## 定位

业务项目应直接依赖所需 Starter；普通单元测试使用 Spring Boot 的测试依赖。签名协议详见 [拦截器模块](../moli-quick-interceptors/README.md)，现代统一异常处理见 [Web 模块](../moli-quick-web-spring-boot-starter/README.md)。
