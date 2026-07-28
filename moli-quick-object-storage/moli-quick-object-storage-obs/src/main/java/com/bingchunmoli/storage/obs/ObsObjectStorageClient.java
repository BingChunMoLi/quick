package com.bingchunmoli.storage.obs;

import com.bingchunmoli.storage.ObjectMetadata;
import com.bingchunmoli.storage.ObjectStorageClient;
import com.bingchunmoli.storage.ObjectStorageException;
import com.bingchunmoli.storage.PutObjectRequest;
import com.bingchunmoli.storage.StorageArguments;
import com.bingchunmoli.storage.StorageObject;
import com.obs.services.ObsClient;
import com.obs.services.model.ObsObject;
import com.obs.services.model.PutObjectResult;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

/**
 * Huawei Cloud OBS adapter.
 */
public class ObsObjectStorageClient implements ObjectStorageClient {

    private static final String PROVIDER = "obs";

    private final ObsClient client;

    public ObsObjectStorageClient(ObsClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public ObjectMetadata putObject(PutObjectRequest request) {
        try {
            com.obs.services.model.ObjectMetadata vendorMetadata = new com.obs.services.model.ObjectMetadata();
            vendorMetadata.setContentLength(request.contentLength());
            if (request.contentType() != null && !request.contentType().isBlank()) {
                vendorMetadata.setContentType(request.contentType());
            }
            request.userMetadata().forEach(vendorMetadata::addUserMetadata);
            com.obs.services.model.PutObjectRequest vendorRequest = new com.obs.services.model.PutObjectRequest(
                    request.bucket(), request.key(), request.content());
            vendorRequest.setMetadata(vendorMetadata);
            PutObjectResult result = client.putObject(vendorRequest);
            return new ObjectMetadata(request.bucket(), request.key(), request.contentLength(), request.contentType(),
                    result.getEtag(), null, request.userMetadata());
        } catch (RuntimeException ex) {
            throw failure("putObject", ex);
        }
    }

    @Override
    public StorageObject getObject(String bucket, String key) {
        StorageArguments.requireText(bucket, "bucket");
        StorageArguments.requireText(key, "key");
        try {
            ObsObject object = client.getObject(bucket, key);
            return new StorageObject(toMetadata(bucket, key, object.getMetadata()), object.getObjectContent());
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
                                      com.obs.services.model.ObjectMetadata metadata) {
        Date lastModified = metadata.getLastModified();
        Instant instant = lastModified == null ? null : lastModified.toInstant();
        Map<String, Object> rawUserMetadata = metadata.getMetadata();
        Map<String, String> userMetadata = rawUserMetadata == null ? Map.of()
                : rawUserMetadata.entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> String.valueOf(entry.getValue())));
        Long contentLength = metadata.getContentLength();
        return new ObjectMetadata(bucket, key, contentLength == null ? 0 : contentLength, metadata.getContentType(),
                metadata.getEtag(), instant, userMetadata);
    }

    private ObjectStorageException failure(String operation, RuntimeException ex) {
        return new ObjectStorageException(PROVIDER, operation, ex);
    }
}
