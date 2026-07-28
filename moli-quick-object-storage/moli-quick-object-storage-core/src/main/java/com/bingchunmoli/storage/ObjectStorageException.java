package com.bingchunmoli.storage;

/**
 * Uniform exception raised by a provider adapter.
 */
public class ObjectStorageException extends RuntimeException {

    private final String provider;
    private final String operation;

    public ObjectStorageException(String provider, String operation, Throwable cause) {
        super("Object storage operation '%s' failed for provider '%s': %s"
                .formatted(operation, provider, cause.getMessage()), cause);
        this.provider = provider;
        this.operation = operation;
    }

    public String getProvider() {
        return provider;
    }

    public String getOperation() {
        return operation;
    }
}
