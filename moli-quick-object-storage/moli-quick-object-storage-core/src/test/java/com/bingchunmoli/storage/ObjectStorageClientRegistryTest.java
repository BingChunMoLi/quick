package com.bingchunmoli.storage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObjectStorageClientRegistryTest {

    @Test
    void shouldResolveProviderCaseInsensitively() {
        ObjectStorageClient oss = client("oss");
        ObjectStorageClient s3 = client("s3");

        ObjectStorageClientRegistry registry = new ObjectStorageClientRegistry(List.of(oss, s3));

        assertThat(registry.get("OSS")).isSameAs(oss);
        assertThat(registry.providers()).containsExactly("oss", "s3");
    }

    @Test
    void shouldRejectDuplicateProvider() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ObjectStorageClientRegistry(List.of(client("s3"), client("S3"))))
                .withMessageContaining("Duplicate");
    }

    private ObjectStorageClient client(String provider) {
        ObjectStorageClient client = mock(ObjectStorageClient.class);
        when(client.provider()).thenReturn(provider);
        return client;
    }
}
