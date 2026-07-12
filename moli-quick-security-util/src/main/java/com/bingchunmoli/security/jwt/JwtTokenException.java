package com.bingchunmoli.security.jwt;

/**
 * JWT validation exception.
 *
 * @author MoLi
 */
public class JwtTokenException extends RuntimeException {

    public JwtTokenException(String message) {
        super(message);
    }

    public JwtTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
