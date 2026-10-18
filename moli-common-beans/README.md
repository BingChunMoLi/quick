# moli-common-beans

公共响应结构、业务码接口和基础枚举；不注册 Spring Bean。

[项目总览](../README.md)

当前源码：Java 17，Spring Boot 依赖管理 4.1.0，Quick 版本 `1.0.13-bate`。依赖示例使用项目总览中的统一版本管理；工具和聚合模块的特殊接入方式见下文。

## 引入依赖

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-common-beans</artifactId>
</dependency>
```

## 使用

```java
import com.bingchunmoli.bean.ResultVO;
import com.bingchunmoli.bean.enums.ProfileEnum;

ResultVO<String> result = ResultVO.ok("hello");
String profile = ProfileEnum.PROD.getProfile();
```

`ResultVO<T>` 包含 `code`、`msg`、`data`，提供 `ok(data)`、`failBySystem(data)` 及带 `Code` 的构造器。业务码可实现 `com.bingchunmoli.bean.Code`，也可使用 `CodeEnum`。环境枚举为 `DEV/PROD/TEST/PRE`；日期时间格式常量见 [DateTimeFormatterOfChina](src/main/java/com/bingchunmoli/bean/DateTimeFormatterOfChina.java)。

## 边界

本模块只是数据模型，不设置 HTTP 状态，也不自动包装控制器返回值。统一异常响应请引入 [Web Starter](../moli-quick-web-spring-boot-starter/README.md)。模型保留 Swagger 2/OpenAPI 注解，业务是否展示文档由宿主决定。
