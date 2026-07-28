package com.bingchunmoli.storage;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registry for applications that use multiple object storage providers.
 */
public final class ObjectStorageClientRegistry {

    private final Map<String, ObjectStorageClient> clients;

    public ObjectStorageClientRegistry(Collection<ObjectStorageClient> clients) {
        if (clients == null || clients.isEmpty()) {
            throw new IllegalArgumentException("At least one ObjectStorageClient is required");
        }
        Map<String, ObjectStorageClient> indexed = new LinkedHashMap<>();
        for (ObjectStorageClient client : clients) {
            if (client == null) {
                throw new IllegalArgumentException("ObjectStorageClient must not be null");
            }
            String provider = normalize(client.provider());
            ObjectStorageClient previous = indexed.putIfAbsent(provider, client);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate object storage provider: " + provider);
            }
        }
        this.clients = Collections.unmodifiableMap(indexed);
    }

    public ObjectStorageClient get(String provider) {
        String normalized = normalize(provider);
        ObjectStorageClient client = clients.get(normalized);
        if (client == null) {
            throw new IllegalArgumentException(
                    "Object storage provider '%s' is not configured. Available providers: %s"
                            .formatted(normalized, clients.keySet()));
        }
        return client;
    }

    public Optional<ObjectStorageClient> find(String provider) {
        return Optional.ofNullable(clients.get(normalize(provider)));
    }

    public Set<String> providers() {
        return clients.keySet();
    }

    public Map<String, ObjectStorageClient> clients() {
        return clients;
    }

    private static String normalize(String provider) {
        return StorageArguments.requireText(provider, "provider").toLowerCase(Locale.ROOT);
    }
}
