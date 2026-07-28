package com.bingchunmoli.storage;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PutObjectRequestTest {

    @Test
    void shouldCreateDefensiveMetadataCopy() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("source", "test");

        PutObjectRequest request = new PutObjectRequest(
                "bucket", "path/file.txt", new ByteArrayInputStream(new byte[0]), 0, "text/plain", metadata);
        metadata.put("source", "changed");

        assertThat(request.userMetadata()).containsEntry("source", "test");
    }

    @Test
    void shouldRejectInvalidArguments() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new PutObjectRequest(" ", "key", new ByteArrayInputStream(new byte[0]), 0, null, Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new PutObjectRequest("bucket", "key", new ByteArrayInputStream(new byte[0]), -1, null, Map.of()));
    }
}
