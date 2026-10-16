package com.bingchunmoli.codegen;

/** User-facing diagnostic. Never attach JDBC exceptions, which may expose credentials. */
public class CodegenException extends RuntimeException {
    public CodegenException(String message) { super(message); }
}
