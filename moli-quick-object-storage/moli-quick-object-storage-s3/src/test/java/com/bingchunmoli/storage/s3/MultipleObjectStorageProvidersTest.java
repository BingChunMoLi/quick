package com.bingchunmoli.storage.s3;

import com.aliyun.oss.OSS;
import com.bingchunmoli.storage.ObjectStorageClient;
import com.bingchunmoli.storage.ObjectStorageClientRegistry;
import com.bingchunmoli.storage.autoconfigure.ObjectStorageRegistryAutoConfiguration;
import com.bingchunmoli.storage.oss.OssObjectStorageAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MultipleObjectStorageProvidersTest {

    @Test
    void shouldEnableOssAndS3AtTheSameTime() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        OssObjectStorageAutoConfiguration.class,
                        S3ObjectStorageAutoConfiguration.class,
                        ObjectStorageRegistryAutoConfiguration.class))
                .withBean(OSS.class, () -> mock(OSS.class))
                .withBean(S3Client.class, () -> mock(S3Client.class))
                .withPropertyValues(
                        "moli.object-storage.oss.enabled=true",
                        "moli.object-storage.s3.enabled=true")
                .run(context -> {
                    assertThat(context).getBeans(ObjectStorageClient.class).hasSize(2);
                    assertThat(context).hasBean("ossObjectStorageClient");
                    assertThat(context).hasBean("s3ObjectStorageClient");
                    assertThat(context).hasSingleBean(ObjectStorageClientRegistry.class);
                    assertThat(context.getBean(ObjectStorageClientRegistry.class).providers())
                            .containsExactlyInAnyOrder("oss", "s3");
                });
    }
}
