package com.bingchunmoli.storage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Provider-neutral operations for an object storage service.
 *
 * <p>Implementations are thread safe when the underlying vendor client is
 * thread safe. Callers must close objects returned by {@link #getObject(String, String)}.</p>
 *
 * @author MoLi
 */
public interface ObjectStorageClient {

    /**
     * Stable provider identifier, for example {@code oss}, {@code obs}, {@code cos} or {@code s3}.
     */
    String provider();

    ObjectMetadata putObject(PutObjectRequest request);

    StorageObject getObject(String bucket, String key);

    ObjectMetadata getObjectMetadata(String bucket, String key);

    boolean objectExists(String bucket, String key);

    void deleteObject(String bucket, String key);

    default ObjectMetadata putObject(String bucket, String key, InputStream content, long contentLength,
                                     String contentType) {
        return putObject(new PutObjectRequest(bucket, key, content, contentLength, contentType, Map.of()));
    }

    default ObjectMetadata putObject(String bucket, String key, byte[] content, String contentType) {
        StorageArguments.requireContent(content);
        return putObject(bucket, key, new ByteArrayInputStream(content), content.length, contentType);
    }

    default ObjectMetadata putObject(String bucket, String key, String content, String contentType) {
        StorageArguments.requireContent(content);
        return putObject(bucket, key, content.getBytes(StandardCharsets.UTF_8), contentType);
    }
}
