# moli-quick-object-storage-s3

Amazon S3 / S3 兼容服务 Provider，向业务暴露统一 ObjectStorageClient，并支持与其他 Provider 共存。

[项目总览](../../README.md) · [所属模块](../README.md)

依赖版本通过项目总览中的 Quick BOM 对齐为当前源码版本 `1.0.13-bate`，Java 17 / Spring Boot 4.1.0。以下示例需与功能族的完整接入说明配合使用。

## 引入

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-object-storage-s3</artifactId>
</dependency>
```

## 配置

```yaml
moli:
  object-storage:
    s3:
      enabled: true
      region: us-east-1
      # endpoint: http://127.0.0.1:9000
      # path-style-access: true
      # access-key: ${S3_ACCESS_KEY}
      # secret-key: ${S3_SECRET_KEY}
      # session-token: ${S3_SESSION_TOKEN:}
```

默认 enabled=false；也兼容单 Provider 选择项 `moli.object-storage.provider: s3`。凭证使用环境变量，不提交真实值。未提供 access-key/secret-key 时使用 AWS SDK 默认凭证链。接入 MinIO 等兼容服务时显式设置 endpoint、path-style-access 及匹配的凭证；兼容程度以目标服务实际支持的 API 为准。

## 使用

```java
// 单 Provider 时直接通过构造器注入 ObjectStorageClient
// 多 Provider 时通过 @Qualifier("s3ObjectStorageClient") 或 Registry 获取
storage.putObject("bucket", "folder/hello.txt", "hello", "text/plain");
try (var object = storage.getObject("bucket", "folder/hello.txt")) {
    byte[] bytes = object.content().readAllBytes();
}
```

同时启用多个 Provider 时使用 `registry.get("s3")`，或显式 Qualifier。公共读写、异常和下载流生命周期见 [Core](../moli-quick-object-storage-core/README.md)。

## 自定义与限制

可声明自己的 S3Client SDK Client Bean；对应厂商 Client 的自动配置会退让。需要替换统一客户端时提供名为 `s3ObjectStorageClient` 的 ObjectStorageClient Bean。只提供自定义 SDK Client 不等于自动创建统一封装，仍应启用本 Provider。

SDK 超时、连接池、重试等高级设置通过自定义 SDK Bean 完成。bucket 和权限由宿主管理；单元测试不代表已完成真实云账号联调。更多多云示例见上级文档。
