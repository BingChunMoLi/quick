package com.bingchunmoli.oauth.client;

/** Coordinates an authorization read/refresh/save cycle. Close always releases ownership. */
public interface RefreshCoordinator extends AutoCloseable {
    Lease acquire(AuthorizedClientRef ref);
    interface Lease extends AutoCloseable {
        void checkOwned();
        @Override void close();
    }
    @Override default void close() { }
}
