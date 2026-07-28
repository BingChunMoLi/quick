package com.bingchunmoli.storage;

import java.util.Objects;

/**
 * Shared validation helpers for provider modules.
 */
public final class StorageArguments {

    private StorageArguments() {
    }

    public static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public static <T> T requireContent(T content) {
        return Objects.requireNonNull(content, "content must not be null");
    }
}
