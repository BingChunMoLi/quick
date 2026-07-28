package com.bingchunmoli.storage.obs;

import com.bingchunmoli.storage.ObjectStorageClient;
import com.obs.services.ObsClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ObsObjectStorageAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObsObjectStorageAutoConfiguration.class))
            .withBean(ObsClient.class, () -> mock(ObsClient.class));

    @Test
    void shouldConfigureSelectedProvider() {
        contextRunner.withPropertyValues("moli.object-storage.provider=obs")
                .run(context -> assertThat(context).hasSingleBean(ObjectStorageClient.class));
    }

    @Test
    void shouldNotConfigureWhenAnotherProviderIsSelected() {
        contextRunner.withPropertyValues("moli.object-storage.provider=oss")
                .run(context -> assertThat(context).doesNotHaveBean(ObjectStorageClient.class));
    }
}
