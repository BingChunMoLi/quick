package com.bingchunmoli.exception;

/**
 * @author moli
 */
public class SignConfigException extends RuntimeException{
    public SignConfigException(String message) {
        super(message);
    }

    public SignConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
