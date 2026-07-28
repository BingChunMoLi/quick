package com.bingchunmoli.storage.oss;

import com.aliyun.oss.OSS;
import com.bingchunmoli.storage.ObjectStorageClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OssObjectStorageAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OssObjectStorageAutoConfiguration.class))
            .withBean(OSS.class, () -> mock(OSS.class));

    @Test
    void shouldConfigureSelectedProvider() {
        contextRunner.withPropertyValues("moli.object-storage.provider=oss")
                .run(context -> assertThat(context).hasSingleBean(ObjectStorageClient.class));
    }

    @Test
    void shouldNotConfigureWhenAnotherProviderIsSelected() {
        contextRunner.withPropertyValues("moli.object-storage.provider=s3")
                .run(context -> assertThat(context).doesNotHaveBean(ObjectStorageClient.class));
    }
}
