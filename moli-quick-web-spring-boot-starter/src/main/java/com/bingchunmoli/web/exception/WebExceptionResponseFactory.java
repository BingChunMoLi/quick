package com.bingchunmoli.web.exception;

import org.springframework.http.ResponseEntity;

/**
 * Converts a normalized error context into the application's HTTP response contract.
 */
@FunctionalInterface
public interface WebExceptionResponseFactory {

    ResponseEntity<?> create(WebErrorContext context);
}
