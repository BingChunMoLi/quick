package com.bingchunmoli.storage.autoconfigure;

import com.bingchunmoli.storage.ObjectStorageClient;
import com.bingchunmoli.storage.ObjectStorageClientRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Creates a provider registry after all provider auto-configurations have run.
 */
@AutoConfiguration(afterName = {
        "com.bingchunmoli.storage.oss.OssObjectStorageAutoConfiguration",
        "com.bingchunmoli.storage.obs.ObsObjectStorageAutoConfiguration",
        "com.bingchunmoli.storage.cos.CosObjectStorageAutoConfiguration",
        "com.bingchunmoli.storage.s3.S3ObjectStorageAutoConfiguration"
})
@ConditionalOnBean(ObjectStorageClient.class)
public class ObjectStorageRegistryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObjectStorageClientRegistry objectStorageClientRegistry(List<ObjectStorageClient> clients) {
        return new ObjectStorageClientRegistry(clients);
    }
}
