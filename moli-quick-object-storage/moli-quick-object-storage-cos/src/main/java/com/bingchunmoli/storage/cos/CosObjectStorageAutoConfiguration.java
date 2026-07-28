package com.bingchunmoli.storage.cos;

import com.bingchunmoli.storage.ObjectStorageClient;
import com.bingchunmoli.storage.StorageArguments;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.BasicSessionCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;

/**
 * Auto-configuration for the Tencent Cloud COS provider.
 */
@AutoConfiguration
@ConditionalOnClass(COSClient.class)
@Conditional(CosObjectStorageAutoConfiguration.CosProviderCondition.class)
@EnableConfigurationProperties(CosStorageProperties.class)
public class CosObjectStorageAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(COSClient.class)
    public COSClient cosClient(CosStorageProperties properties) {
        String region = StorageArguments.requireText(properties.getRegion(), "moli.object-storage.cos.region");
        String secretId = StorageArguments.requireText(
                properties.getSecretId(), "moli.object-storage.cos.secret-id");
        String secretKey = StorageArguments.requireText(
                properties.getSecretKey(), "moli.object-storage.cos.secret-key");
        COSCredentials credentials;
        if (properties.getSessionToken() != null && !properties.getSessionToken().isBlank()) {
            credentials = new BasicSessionCredentials(secretId, secretKey, properties.getSessionToken());
        } else {
            credentials = new BasicCOSCredentials(secretId, secretKey);
        }
        return new COSClient(credentials, new ClientConfig(new Region(region)));
    }

    @Bean(name = "cosObjectStorageClient")
    @ConditionalOnMissingBean(name = "cosObjectStorageClient")
    public ObjectStorageClient cosObjectStorageClient(COSClient cosClient) {
        return new CosObjectStorageClient(cosClient);
    }

    static final class CosProviderCondition extends AnyNestedCondition {

        CosProviderCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "moli.object-storage", name = "provider", havingValue = "cos")
        static class SelectedProvider {
        }

        @ConditionalOnProperty(prefix = "moli.object-storage.cos", name = "enabled", havingValue = "true")
        static class EnabledProvider {
        }
    }
}
