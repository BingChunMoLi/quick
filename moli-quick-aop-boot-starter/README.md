# moli-quick-aop-boot-starter

通过显式启用注解接入方法日志和执行时间切面。

[项目总览](../README.md)

当前源码：Java 17，Spring Boot 依赖管理 4.1.0，Quick 版本 `1.0.13-bate`。依赖示例使用项目总览中的统一版本管理；工具和聚合模块的特殊接入方式见下文。

## 引入与启用

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-aop-boot-starter</artifactId>
</dependency>
```


```java
import com.bingchunmoli.annotation.EnableAop;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableAop
@SpringBootApplication
public class Application { }
```

也可只使用 `@EnableLog` 或 `@EnableExecutionTime` 导入对应切面；仅添加依赖不会自动导入这些切面。

## 方法示例

```java
import com.bingchunmoli.annotation.ExecutionTime;
import com.bingchunmoli.annotation.Log;

@Log(value = "查询商品", module = "catalog")
@ExecutionTime("loadProduct")
public String loadProduct() {
    return "product";
}
```

方法必须位于 Spring 管理且被代理的 Bean，由其他 Bean 调用；同类内部直接调用不会经过 Spring AOP。`@Log` 记录入参、返回值或异常，敏感方法应先做数据脱敏。时间统计输出在 DEBUG 级别；`timeunit` 字段目前为预留参数。

## 当前限制

执行时间切面使用共享 `StopWatch`，不适合作为并发压测或嵌套调用的统计工具。日志切面包含 `@Async`，是否启用异步由宿主的 `@EnableAsync` 和执行器配置决定。本模块无独立 YAML 配置项。
