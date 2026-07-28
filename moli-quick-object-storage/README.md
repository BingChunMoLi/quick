# moli-quick-object-storage

面向 Spring Boot 的可插拔对象存储模块。业务代码只依赖统一的
`ObjectStorageClient`，可以单独或同时使用阿里云 OSS、华为云 OBS、腾讯云 COS、
Amazon S3 及其他 S3 兼容服务。

## 模块

| artifactId | 用途 |
| --- | --- |
| `moli-quick-object-storage-core` | 无 Spring、无厂商 SDK 的公共 API |
| `moli-quick-object-storage-spring-boot-autoconfigure` | 多 Provider 注册表自动配置 |
| `moli-quick-object-storage-oss` | 阿里云 OSS Provider Starter |
| `moli-quick-object-storage-obs` | 华为云 OBS Provider Starter |
| `moli-quick-object-storage-cos` | 腾讯云 COS Provider Starter |
| `moli-quick-object-storage-s3` | Amazon S3 及 S3 兼容 Provider Starter |

应用只需引入实际使用的 Provider；需要同时使用多个时，引入多个 Provider 依赖。
例如使用 OSS：

```xml
<dependency>
    <groupId>com.bingchunmoli</groupId>
    <artifactId>moli-quick-object-storage-oss</artifactId>
</dependency>
```

## 配置

不要在版本库中提交真实凭证，推荐通过环境变量、工作负载身份或密钥管理服务注入。

单 Provider 模式兼容原有的 `moli.object-storage.provider=oss` 配置。多 Provider
模式则在每个需要启用的 Provider 下设置 `enabled: true`。

### 同时启用多个 Provider

```yaml
moli:
  object-storage:
    oss:
      enabled: true
      endpoint: https://oss-cn-hangzhou.aliyuncs.com
      access-key-id: ${OSS_ACCESS_KEY_ID}
      access-key-secret: ${OSS_ACCESS_KEY_SECRET}
    s3:
      enabled: true
      region: ap-southeast-1
```

此时会注册 `ossObjectStorageClient`、`s3ObjectStorageClient` 两个 Bean，以及统一的
`ObjectStorageClientRegistry`。

### 阿里云 OSS

```yaml
moli:
  object-storage:
    provider: oss
    oss:
      endpoint: https://oss-cn-hangzhou.aliyuncs.com
      access-key-id: ${OSS_ACCESS_KEY_ID}
      access-key-secret: ${OSS_ACCESS_KEY_SECRET}
      # security-token: ${OSS_SECURITY_TOKEN:}
```

### 华为云 OBS

```yaml
moli:
  object-storage:
    provider: obs
    obs:
      endpoint: https://obs.cn-north-4.myhuaweicloud.com
      access-key: ${OBS_ACCESS_KEY}
      secret-key: ${OBS_SECRET_KEY}
      # security-token: ${OBS_SECURITY_TOKEN:}
```

### 腾讯云 COS

```yaml
moli:
  object-storage:
    provider: cos
    cos:
      region: ap-guangzhou
      secret-id: ${COS_SECRET_ID}
      secret-key: ${COS_SECRET_KEY}
      # session-token: ${COS_SESSION_TOKEN:}
```

### Amazon S3

不配置 `access-key` 和 `secret-key` 时使用 AWS SDK 默认凭证链。

```yaml
moli:
  object-storage:
    provider: s3
    s3:
      region: ap-southeast-1
      # access-key: ${AWS_ACCESS_KEY_ID}
      # secret-key: ${AWS_SECRET_ACCESS_KEY}
      # session-token: ${AWS_SESSION_TOKEN:}
```

对于 MinIO 等 S3 兼容服务，可额外设置：

```yaml
moli:
  object-storage:
    provider: s3
    s3:
      region: us-east-1
      endpoint: http://127.0.0.1:9000
      path-style-access: true
      access-key: ${S3_ACCESS_KEY}
      secret-key: ${S3_SECRET_KEY}
```

## 使用

```java
import com.bingchunmoli.storage.ObjectStorageClient;
import com.bingchunmoli.storage.StorageObject;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
class FileService {

    private final ObjectStorageClient storage;

    FileService(ObjectStorageClient storage) {
        this.storage = storage;
    }

    void upload(String bucket, String key, byte[] content) {
        storage.putObject(bucket, key, content, "application/octet-stream");
    }

    byte[] download(String bucket, String key) throws IOException {
        try (StorageObject object = storage.getObject(bucket, key)) {
            return object.content().readAllBytes();
        }
    }

    void delete(String bucket, String key) {
        storage.deleteObject(bucket, key);
    }
}
```

公共接口还提供 `getObjectMetadata` 和 `objectExists`。下载返回的
`StorageObject` 持有底层响应流，必须使用 try-with-resources 关闭。

如果应用提供了自己的厂商 Client Bean，SDK Client 自动配置会退让；自定义统一客户端时，
使用对应的标准 Bean 名即可替换指定 Provider。

### 多 Provider 动态选择

```java
import com.bingchunmoli.storage.ObjectStorageClient;
import com.bingchunmoli.storage.ObjectStorageClientRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
class MultiCloudFileService {

    private final ObjectStorageClientRegistry registry;

    MultiCloudFileService(ObjectStorageClientRegistry registry) {
        this.registry = registry;
    }

    void upload(String provider, String bucket, String key, byte[] content) {
        registry.get(provider).putObject(bucket, key, content, "application/octet-stream");
    }
}
```

也可以通过限定符直接注入指定实现：

```java
MultiCloudFileService(
        @Qualifier("ossObjectStorageClient") ObjectStorageClient oss,
        @Qualifier("s3ObjectStorageClient") ObjectStorageClient s3) {
    // ...
}
```

启用多个 Provider 后，直接注入单个无 `@Qualifier` 的 `ObjectStorageClient` 会产生歧义；
此时应使用注册表或限定符。自定义某个 Provider 实现时，请使用对应的标准 Bean 名。
