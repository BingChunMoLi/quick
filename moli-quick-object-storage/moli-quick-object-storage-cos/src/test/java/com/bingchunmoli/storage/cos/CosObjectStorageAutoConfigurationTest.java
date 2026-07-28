package com.bingchunmoli.storage.cos;

import com.bingchunmoli.storage.ObjectStorageClient;
import com.qcloud.cos.COSClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CosObjectStorageAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosObjectStorageAutoConfiguration.class))
            .withBean(COSClient.class, () -> mock(COSClient.class));

    @Test
    void shouldConfigureSelectedProvider() {
        contextRunner.withPropertyValues("moli.object-storage.provider=cos")
                .run(context -> assertThat(context).hasSingleBean(ObjectStorageClient.class));
    }

    @Test
    void shouldNotConfigureWhenAnotherProviderIsSelected() {
        contextRunner.withPropertyValues("moli.object-storage.provider=obs")
                .run(context -> assertThat(context).doesNotHaveBean(ObjectStorageClient.class));
    }
}
