package com.bingchunmoli.web.request;

/**
 * Generates request identifiers when an incoming identifier cannot be reused.
 */
@FunctionalInterface
public interface RequestIdGenerator {

    String generate();
}
