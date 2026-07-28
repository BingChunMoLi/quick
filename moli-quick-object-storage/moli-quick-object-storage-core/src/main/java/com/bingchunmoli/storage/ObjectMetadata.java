package com.bingchunmoli.storage;

import java.time.Instant;
import java.util.Map;

/**
 * Provider-neutral object metadata.
 */
public record ObjectMetadata(
        String bucket,
        String key,
        long contentLength,
        String contentType,
        String eTag,
        Instant lastModified,
        Map<String, String> userMetadata
) {
    public ObjectMetadata {
        bucket = StorageArguments.requireText(bucket, "bucket");
        key = StorageArguments.requireText(key, "key");
        userMetadata = userMetadata == null ? Map.of() : Map.copyOf(userMetadata);
    }
}
