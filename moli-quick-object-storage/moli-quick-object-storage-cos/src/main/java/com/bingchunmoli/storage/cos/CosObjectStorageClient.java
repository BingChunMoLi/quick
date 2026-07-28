package com.bingchunmoli.storage.cos;

import com.bingchunmoli.storage.ObjectMetadata;
import com.bingchunmoli.storage.ObjectStorageClient;
import com.bingchunmoli.storage.ObjectStorageException;
import com.bingchunmoli.storage.PutObjectRequest;
import com.bingchunmoli.storage.StorageArguments;
import com.bingchunmoli.storage.StorageObject;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.PutObjectResult;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

/**
 * Tencent Cloud COS adapter.
 */
public class CosObjectStorageClient implements ObjectStorageClient {

    private static final String PROVIDER = "cos";

    private final COSClient client;

    public CosObjectStorageClient(COSClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public ObjectMetadata putObject(PutObjectRequest request) {
        try {
            com.qcloud.cos.model.ObjectMetadata vendorMetadata = new com.qcloud.cos.model.ObjectMetadata();
            vendorMetadata.setContentLength(request.contentLength());
            if (request.contentType() != null && !request.contentType().isBlank()) {
                vendorMetadata.setContentType(request.contentType());
            }
            vendorMetadata.setUserMetadata(request.userMetadata());
            com.qcloud.cos.model.PutObjectRequest vendorRequest = new com.qcloud.cos.model.PutObjectRequest(
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
            COSObject object = client.getObject(bucket, key);
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
                                      com.qcloud.cos.model.ObjectMetadata metadata) {
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
