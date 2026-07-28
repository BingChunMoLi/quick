package com.bingchunmoli.storage.s3;

import com.bingchunmoli.storage.ObjectMetadata;
import com.bingchunmoli.storage.ObjectStorageClient;
import com.bingchunmoli.storage.ObjectStorageException;
import com.bingchunmoli.storage.PutObjectRequest;
import com.bingchunmoli.storage.StorageArguments;
import com.bingchunmoli.storage.StorageObject;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Map;
import java.util.Objects;

/**
 * Amazon S3 and S3-compatible storage adapter.
 */
public class S3ObjectStorageClient implements ObjectStorageClient {

    private static final String PROVIDER = "s3";

    private final S3Client client;

    public S3ObjectStorageClient(S3Client client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public ObjectMetadata putObject(PutObjectRequest request) {
        try {
            software.amazon.awssdk.services.s3.model.PutObjectRequest.Builder builder =
                    software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                            .bucket(request.bucket())
                            .key(request.key())
                            .metadata(request.userMetadata());
            if (request.contentType() != null && !request.contentType().isBlank()) {
                builder.contentType(request.contentType());
            }
            software.amazon.awssdk.services.s3.model.PutObjectResponse response = client.putObject(
                    builder.build(), RequestBody.fromInputStream(request.content(), request.contentLength()));
            return new ObjectMetadata(request.bucket(), request.key(), request.contentLength(), request.contentType(),
                    response.eTag(), null, request.userMetadata());
        } catch (RuntimeException ex) {
            throw failure("putObject", ex);
        }
    }

    @Override
    public StorageObject getObject(String bucket, String key) {
        StorageArguments.requireText(bucket, "bucket");
        StorageArguments.requireText(key, "key");
        try {
            ResponseInputStream<GetObjectResponse> responseStream = client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            return new StorageObject(toMetadata(bucket, key, responseStream.response()), responseStream);
        } catch (RuntimeException ex) {
            throw failure("getObject", ex);
        }
    }

    @Override
    public ObjectMetadata getObjectMetadata(String bucket, String key) {
        StorageArguments.requireText(bucket, "bucket");
        StorageArguments.requireText(key, "key");
        try {
            HeadObjectResponse response = client.headObject(
                    HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return toMetadata(bucket, key, response);
        } catch (RuntimeException ex) {
            throw failure("getObjectMetadata", ex);
        }
    }

    @Override
    public boolean objectExists(String bucket, String key) {
        StorageArguments.requireText(bucket, "bucket");
        StorageArguments.requireText(key, "key");
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw failure("objectExists", ex);
        } catch (RuntimeException ex) {
            throw failure("objectExists", ex);
        }
    }

    @Override
    public void deleteObject(String bucket, String key) {
        StorageArguments.requireText(bucket, "bucket");
        StorageArguments.requireText(key, "key");
        try {
            client.deleteObject(builder -> builder.bucket(bucket).key(key));
        } catch (RuntimeException ex) {
            throw failure("deleteObject", ex);
        }
    }

    private ObjectMetadata toMetadata(String bucket, String key, GetObjectResponse response) {
        return new ObjectMetadata(bucket, key, valueOrZero(response.contentLength()), response.contentType(),
                response.eTag(), response.lastModified(), safeMetadata(response.metadata()));
    }

    private ObjectMetadata toMetadata(String bucket, String key, HeadObjectResponse response) {
        return new ObjectMetadata(bucket, key, valueOrZero(response.contentLength()), response.contentType(),
                response.eTag(), response.lastModified(), safeMetadata(response.metadata()));
    }

    private long valueOrZero(Long value) {
        return value == null ? 0 : value;
    }

    private Map<String, String> safeMetadata(Map<String, String> metadata) {
        return metadata == null ? Map.of() : metadata;
    }

    private ObjectStorageException failure(String operation, RuntimeException ex) {
        return new ObjectStorageException(PROVIDER, operation, ex);
    }
}
