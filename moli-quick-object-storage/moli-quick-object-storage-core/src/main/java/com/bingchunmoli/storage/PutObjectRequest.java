package com.bingchunmoli.storage;

import java.io.InputStream;
import java.util.Map;

/**
 * Request used to upload an object.
 */
public record PutObjectRequest(
        String bucket,
        String key,
        InputStream content,
        long contentLength,
        String contentType,
        Map<String, String> userMetadata
) {
    public PutObjectRequest {
        bucket = StorageArguments.requireText(bucket, "bucket");
        key = StorageArguments.requireText(key, "key");
        content = StorageArguments.requireContent(content);
        if (contentLength < 0) {
            throw new IllegalArgumentException("contentLength must not be negative");
        }
        userMetadata = userMetadata == null ? Map.of() : Map.copyOf(userMetadata);
    }
}
