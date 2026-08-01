package com.bingchunmoli.web.request;

import java.util.UUID;

/**
 * Generates compact random UUID request identifiers.
 */
public class UuidRequestIdGenerator implements RequestIdGenerator {

    @Override
    public String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
