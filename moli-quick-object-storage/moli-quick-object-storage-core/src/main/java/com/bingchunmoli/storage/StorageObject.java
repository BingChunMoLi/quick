package com.bingchunmoli.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Downloaded object and its stream. Closing this object closes the provider response stream.
 */
public final class StorageObject implements AutoCloseable {

    private final ObjectMetadata metadata;
    private final InputStream content;

    public StorageObject(ObjectMetadata metadata, InputStream content) {
        this.metadata = Objects.requireNonNull(metadata, "metadata must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
    }

    public ObjectMetadata metadata() {
        return metadata;
    }

    public InputStream content() {
        return content;
    }

    @Override
    public void close() throws IOException {
        content.close();
    }
}
