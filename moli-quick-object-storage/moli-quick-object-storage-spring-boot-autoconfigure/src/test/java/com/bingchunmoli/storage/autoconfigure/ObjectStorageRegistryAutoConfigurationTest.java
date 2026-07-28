package com.bingchunmoli.storage.autoconfigure;

import com.bingchunmoli.storage.ObjectStorageClient;
import com.bingchunmoli.storage.ObjectStorageClientRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObjectStorageRegistryAutoConfigurationTest {

    @Test
    void shouldCollectMultipleProviders() {
        ObjectStorageClient oss = client("oss");
        ObjectStorageClient s3 = client("s3");

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ObjectStorageRegistryAutoConfiguration.class))
                .withBean("ossObjectStorageClient", ObjectStorageClient.class, () -> oss)
                .withBean("s3ObjectStorageClient", ObjectStorageClient.class, () -> s3)
                .run(context -> {
                    assertThat(context).hasSingleBean(ObjectStorageClientRegistry.class);
                    assertThat(context.getBean(ObjectStorageClientRegistry.class).providers())
                            .containsExactly("oss", "s3");
                });
    }

    private ObjectStorageClient client(String provider) {
        ObjectStorageClient client = mock(ObjectStorageClient.class);
        when(client.provider()).thenReturn(provider);
        return client;
    }
}
