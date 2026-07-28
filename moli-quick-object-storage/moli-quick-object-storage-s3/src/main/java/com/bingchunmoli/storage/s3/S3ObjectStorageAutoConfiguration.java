package com.bingchunmoli.storage.s3;

import com.bingchunmoli.storage.ObjectStorageClient;
import com.bingchunmoli.storage.StorageArguments;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Auto-configuration for Amazon S3 and S3-compatible providers.
 */
@AutoConfiguration
@ConditionalOnClass(S3Client.class)
@Conditional(S3ObjectStorageAutoConfiguration.S3ProviderCondition.class)
@EnableConfigurationProperties(S3StorageProperties.class)
public class S3ObjectStorageAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(S3Client.class)
    public S3Client s3Client(S3StorageProperties properties) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(StorageArguments.requireText(
                        properties.getRegion(), "moli.object-storage.s3.region")))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccess())
                        .build());
        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }
        boolean hasAccessKey = properties.getAccessKey() != null && !properties.getAccessKey().isBlank();
        boolean hasSecretKey = properties.getSecretKey() != null && !properties.getSecretKey().isBlank();
        if (hasAccessKey != hasSecretKey) {
            throw new IllegalArgumentException(
                    "moli.object-storage.s3.access-key and secret-key must be configured together");
        }
        if (hasAccessKey) {
            if (properties.getSessionToken() != null && !properties.getSessionToken().isBlank()) {
                builder.credentialsProvider(StaticCredentialsProvider.create(AwsSessionCredentials.create(
                        properties.getAccessKey(), properties.getSecretKey(), properties.getSessionToken())));
            } else {
                builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        properties.getAccessKey(), properties.getSecretKey())));
            }
        }
        return builder.build();
    }

    @Bean(name = "s3ObjectStorageClient")
    @ConditionalOnMissingBean(name = "s3ObjectStorageClient")
    public ObjectStorageClient s3ObjectStorageClient(S3Client s3Client) {
        return new S3ObjectStorageClient(s3Client);
    }

    static final class S3ProviderCondition extends AnyNestedCondition {

        S3ProviderCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "moli.object-storage", name = "provider", havingValue = "s3")
        static class SelectedProvider {
        }

        @ConditionalOnProperty(prefix = "moli.object-storage.s3", name = "enabled", havingValue = "true")
        static class EnabledProvider {
        }
    }
}
