package com.bingchunmoli.exception;

/**
 * 签名配置异常
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
