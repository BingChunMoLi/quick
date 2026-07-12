package com.bingchunmoli.security.jwt;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * Resolves JWT token from an HTTP request.
 *
 * @author MoLi
 */
public class JwtTokenResolver {

    private final JwtTokenProperties properties;

    public JwtTokenResolver(JwtTokenProperties properties) {
        this.properties = properties;
    }

    public Optional<String> resolve(HttpServletRequest request) {
        String header = request.getHeader(properties.getHeaderName());
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        String prefix = properties.getTokenPrefix();
        if (prefix != null && !prefix.isBlank()) {
            if (!header.startsWith(prefix)) {
                return Optional.empty();
            }
            return Optional.of(header.substring(prefix.length()).trim());
        }
        return Optional.of(header.trim());
    }
}
