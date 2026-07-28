package com.bingchunmoli.storage.obs;

import com.bingchunmoli.storage.ObjectStorageClient;
import com.bingchunmoli.storage.StorageArguments;
import com.obs.services.ObsClient;
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
 * Auto-configuration for the Huawei Cloud OBS provider.
 */
@AutoConfiguration
@ConditionalOnClass(ObsClient.class)
@Conditional(ObsObjectStorageAutoConfiguration.ObsProviderCondition.class)
@EnableConfigurationProperties(ObsStorageProperties.class)
public class ObsObjectStorageAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(ObsClient.class)
    public ObsClient obsClient(ObsStorageProperties properties) {
        String endpoint = StorageArguments.requireText(properties.getEndpoint(), "moli.object-storage.obs.endpoint");
        String accessKey = StorageArguments.requireText(
                properties.getAccessKey(), "moli.object-storage.obs.access-key");
        String secretKey = StorageArguments.requireText(
                properties.getSecretKey(), "moli.object-storage.obs.secret-key");
        if (properties.getSecurityToken() != null && !properties.getSecurityToken().isBlank()) {
            return new ObsClient(accessKey, secretKey, properties.getSecurityToken(), endpoint);
        }
        return new ObsClient(accessKey, secretKey, endpoint);
    }

    @Bean(name = "obsObjectStorageClient")
    @ConditionalOnMissingBean(name = "obsObjectStorageClient")
    public ObjectStorageClient obsObjectStorageClient(ObsClient obsClient) {
        return new ObsObjectStorageClient(obsClient);
    }

    static final class ObsProviderCondition extends AnyNestedCondition {

        ObsProviderCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "moli.object-storage", name = "provider", havingValue = "obs")
        static class SelectedProvider {
        }

        @ConditionalOnProperty(prefix = "moli.object-storage.obs", name = "enabled", havingValue = "true")
        static class EnabledProvider {
        }
    }
}
