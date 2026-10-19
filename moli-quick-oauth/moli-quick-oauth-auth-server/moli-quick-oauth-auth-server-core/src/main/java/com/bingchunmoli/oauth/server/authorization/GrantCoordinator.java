package com.bingchunmoli.oauth.server.authorization;
import java.util.function.Supplier;
/** Coordinates an entire native grant authentication, including its persistence. */
public interface GrantCoordinator {
    <T> T execute(String authorizationId, Supplier<T> work);
    default void checkOwned() { }
}
