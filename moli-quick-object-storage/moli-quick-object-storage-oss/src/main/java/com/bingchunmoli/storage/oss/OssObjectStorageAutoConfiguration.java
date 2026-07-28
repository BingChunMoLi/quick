package com.bingchunmoli.storage.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.bingchunmoli.storage.ObjectStorageClient;
import com.bingchunmoli.storage.StorageArguments;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;

/**
 * Auto-configuration for the Alibaba Cloud OSS provider.
 */
@AutoConfiguration
@ConditionalOnClass(OSS.class)
@Conditional(OssObjectStorageAutoConfiguration.OssProviderCondition.class)
@EnableConfigurationProperties(OssStorageProperties.class)
public class OssObjectStorageAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(OSS.class)
    public OSS ossClient(OssStorageProperties properties) {
        String endpoint = StorageArguments.requireText(properties.getEndpoint(), "moli.object-storage.oss.endpoint");
        String accessKeyId = StorageArguments.requireText(
                properties.getAccessKeyId(), "moli.object-storage.oss.access-key-id");
        String accessKeySecret = StorageArguments.requireText(
                properties.getAccessKeySecret(), "moli.object-storage.oss.access-key-secret");
        if (properties.getSecurityToken() != null && !properties.getSecurityToken().isBlank()) {
            return new OSSClientBuilder().build(
                    endpoint, accessKeyId, accessKeySecret, properties.getSecurityToken());
        }
        return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }

    @Bean(name = "ossObjectStorageClient")
    @ConditionalOnMissingBean(name = "ossObjectStorageClient")
    public ObjectStorageClient ossObjectStorageClient(OSS ossClient) {
        return new OssObjectStorageClient(ossClient);
    }

    static final class OssProviderCondition extends AnyNestedCondition {

        OssProviderCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "moli.object-storage", name = "provider", havingValue = "oss")
        static class SelectedProvider {
        }

        @ConditionalOnProperty(prefix = "moli.object-storage.oss", name = "enabled", havingValue = "true")
        static class EnabledProvider {
        }
    }
}
