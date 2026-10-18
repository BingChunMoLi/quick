# moli-quick-object-storage-spring-boot-autoconfigure

发现统一 ObjectStorageClient Bean 并提供多 Provider 注册表；不创建厂商 SDK Client。

[项目总览](../../README.md) · [所属模块](../README.md)

依赖版本通过项目总览中的 Quick BOM 对齐为当前源码版本 `1.0.13-bate`，Java 17 / Spring Boot 4.1.0。以下示例需与功能族的完整接入说明配合使用。

## 接入

通常由具体 Provider 模块传递引入；自定义 Provider 可以直接依赖：

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-object-storage-spring-boot-autoconfigure</artifactId>
</dependency>
```

需要宿主至少提供一个 `ObjectStorageClient` Bean。存在客户端且缺少自定义注册表时，会创建 `ObjectStorageClientRegistry`。

## 选择 Provider

```java
// 通过构造器注入 ObjectStorageClientRegistry registry
registry.get("s3").putObject("bucket", "hello.txt", "hello", "text/plain");
var configured = registry.providers();
var optional = registry.find("oss");
```

Provider ID 按大小写归一化；重复 ID 和不存在的必选 ID 会报错。单个客户端可以直接注入，多客户端时使用 Registry 或对应名称的 `@Qualifier`，不依赖随机 Bean 选择。

## 扩展

业务可提供自己的注册表 Bean；具体 SDK Client 和统一客户端的替换规则见各 Provider README。此模块没有独立配置前缀，Provider 启用开关位于 `moli.object-storage.<provider>.enabled`，多 Provider 选择与通用接口见上级文档。
