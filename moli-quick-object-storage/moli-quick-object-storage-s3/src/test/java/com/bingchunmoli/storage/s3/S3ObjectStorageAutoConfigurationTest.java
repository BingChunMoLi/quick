package com.bingchunmoli.storage.s3;

import com.bingchunmoli.storage.ObjectStorageClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class S3ObjectStorageAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(S3ObjectStorageAutoConfiguration.class))
            .withBean(S3Client.class, () -> mock(S3Client.class));

    @Test
    void shouldConfigureSelectedProvider() {
        contextRunner.withPropertyValues("moli.object-storage.provider=s3")
                .run(context -> assertThat(context).hasSingleBean(ObjectStorageClient.class));
    }

    @Test
    void shouldNotConfigureWhenAnotherProviderIsSelected() {
        contextRunner.withPropertyValues("moli.object-storage.provider=cos")
                .run(context -> assertThat(context).doesNotHaveBean(ObjectStorageClient.class));
    }
}
