# moli-quick-object-storage-core

不依赖 Spring 或厂商 SDK 的统一对象存储 API，用于隔离业务与具体云存储实现。

[项目总览](../../README.md) · [所属模块](../README.md)

依赖版本通过项目总览中的 Quick BOM 对齐为当前源码版本 `1.0.13-bate`，Java 17 / Spring Boot 4.1.0。以下示例需与功能族的完整接入说明配合使用。

## 引入与契约

```xml
<dependency>
  <groupId>com.bingchunmoli</groupId>
  <artifactId>moli-quick-object-storage-core</artifactId>
</dependency>
```

本模块不创建真实客户端，不包含自动配置。实际接入需选择 OSS/OBS/COS/S3 Provider，或自行实现 `ObjectStorageClient`。

| 类型 | 职责 |
| --- | --- |
| `ObjectStorageClient` | 上传、下载、元数据、存在性、删除 |
| `PutObjectRequest` | bucket/key、输入流、明确长度、contentType、用户元数据 |
| `StorageObject` | 持有下载响应流，需关闭 |
| `ObjectMetadata` | 统一对象元数据 |
| `ObjectStorageClientRegistry` | 按 provider 标识选择客户端 |
| `ObjectStorageException` | 统一异常入口 |

## 使用

```java
import com.bingchunmoli.storage.ObjectStorageClient;
import com.bingchunmoli.storage.StorageObject;

// storage 为调用方提供的 ObjectStorageClient
storage.putObject("bucket", "docs/readme.txt", "hello", "text/plain");
try (StorageObject object = storage.getObject("bucket", "docs/readme.txt")) {
    byte[] bytes = object.content().readAllBytes();
}
boolean exists = storage.objectExists("bucket", "docs/readme.txt");
storage.deleteObject("bucket", "docs/readme.txt");
```

## 边界

上传流长度必须明确；下载使用 try-with-resources 释放响应。接口线程安全能力取决于底层 Client。不会自动创建 bucket、配置访问策略或重试业务写操作。更多 SDK 定制和多 Provider 示例见上级 README。
