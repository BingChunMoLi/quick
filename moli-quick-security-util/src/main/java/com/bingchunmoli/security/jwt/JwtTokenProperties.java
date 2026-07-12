package com.bingchunmoli.security.jwt;

import java.time.Duration;

/**
 * JWT token settings usable with or without Spring Boot auto configuration.
 *
 * @author MoLi
 */
public class JwtTokenProperties {

    private boolean enabled = true;

    private JwtMode mode = JwtMode.SIMPLE;

    private String issuer = "moli-quick";

    private String secret = "moli-quick-security-default-secret-key-change-me";

    private Duration timeout = Duration.ofHours(2);

    private Duration activeTimeout = Duration.ofMinutes(30);

    private String headerName = "Authorization";

    private String tokenPrefix = "Bearer ";

    private String sessionStoreClassName;

    private String redisKeyPrefix = "moli:security:jwt";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public JwtMode getMode() {
        return mode;
    }

    public void setMode(JwtMode mode) {
        this.mode = mode == null ? JwtMode.SIMPLE : mode;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout == null ? Duration.ofHours(2) : timeout;
    }

    public Duration getActiveTimeout() {
        return activeTimeout;
    }

    public void setActiveTimeout(Duration activeTimeout) {
        this.activeTimeout = activeTimeout == null ? Duration.ZERO : activeTimeout;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public void setTokenPrefix(String tokenPrefix) {
        this.tokenPrefix = tokenPrefix;
    }

    public String getSessionStoreClassName() {
        return sessionStoreClassName;
    }

    public void setSessionStoreClassName(String sessionStoreClassName) {
        this.sessionStoreClassName = sessionStoreClassName;
    }

    public String getRedisKeyPrefix() {
        return redisKeyPrefix;
    }

    public void setRedisKeyPrefix(String redisKeyPrefix) {
        this.redisKeyPrefix = redisKeyPrefix;
    }
}
