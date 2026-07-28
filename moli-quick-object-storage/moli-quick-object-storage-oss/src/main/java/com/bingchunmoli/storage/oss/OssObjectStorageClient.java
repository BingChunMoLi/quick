package com.bingchunmoli.storage.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PutObjectResult;
import com.bingchunmoli.storage.ObjectMetadata;
import com.bingchunmoli.storage.ObjectStorageClient;
import com.bingchunmoli.storage.ObjectStorageException;
import com.bingchunmoli.storage.PutObjectRequest;
import com.bingchunmoli.storage.StorageArguments;
import com.bingchunmoli.storage.StorageObject;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

/**
 * Alibaba Cloud OSS adapter.
 */
public class OssObjectStorageClient implements ObjectStorageClient {

    private static final String PROVIDER = "oss";

    private final OSS client;

    public OssObjectStorageClient(OSS client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public ObjectMetadata putObject(PutObjectRequest request) {
        try {
            com.aliyun.oss.model.ObjectMetadata vendorMetadata = new com.aliyun.oss.model.ObjectMetadata();
            vendorMetadata.setContentLength(request.contentLength());
            if (request.contentType() != null && !request.contentType().isBlank()) {
                vendorMetadata.setContentType(request.contentType());
            }
            vendorMetadata.setUserMetadata(request.userMetadata());
            com.aliyun.oss.model.PutObjectRequest vendorRequest = new com.aliyun.oss.model.PutObjectRequest(
                    request.bucket(), request.key(), request.content(), vendorMetadata);
            PutObjectResult result = client.putObject(vendorRequest);
            return new ObjectMetadata(request.bucket(), request.key(), request.contentLength(), request.contentType(),
                    result.getETag(), null, request.userMetadata());
        } catch (RuntimeException ex) {
            throw failure("putObject", ex);
        }
    }

    @Override
    public StorageObject getObject(String bucket, String key) {
        StorageArguments.requireText(bucket, "bucket");
        StorageArguments.requireText(key, "key");
        try {
            OSSObject object = client.getObject(bucket, key);
            return new StorageObject(toMetadata(bucket, key, object.getObjectMetadata()), object.getObjectContent());
        } catch (RuntimeException ex) {
            throw failure("getObject", ex);
        }
    }

    @Override
    public ObjectMetadata getObjectMetadata(String bucket, String key) {
        StorageArguments.requireText(bucket, "bucket");
        StorageArguments.requireText(key, "key");
        try {
            return toMetadata(bucket, key, client.getObjectMetadata(bucket, key));
        } catch (RuntimeException ex) {
            throw failure("getObjectMetadata", ex);
        }
    }

    @Override
    public boolean objectExists(String bucket, String key) {
        StorageArguments.requireText(bucket, "bucket");
        StorageArguments.requireText(key, "key");
        try {
            return client.doesObjectExist(bucket, key);
        } catch (RuntimeException ex) {
            throw failure("objectExists", ex);
        }
    }

    @Override
    public void deleteObject(String bucket, String key) {
        StorageArguments.requireText(bucket, "bucket");
        StorageArguments.requireText(key, "key");
        try {
            client.deleteObject(bucket, key);
        } catch (RuntimeException ex) {
            throw failure("deleteObject", ex);
        }
    }

    private ObjectMetadata toMetadata(String bucket, String key,
                                      com.aliyun.oss.model.ObjectMetadata metadata) {
        Date lastModified = metadata.getLastModified();
        Instant instant = lastModified == null ? null : lastModified.toInstant();
        Map<String, String> userMetadata = metadata.getUserMetadata();
        return new ObjectMetadata(bucket, key, metadata.getContentLength(), metadata.getContentType(),
                metadata.getETag(), instant, userMetadata);
    }

    private ObjectStorageException failure(String operation, RuntimeException ex) {
        return new ObjectStorageException(PROVIDER, operation, ex);
    }
}
