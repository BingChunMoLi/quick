package com.bingchunmoli.security.jwt;

import java.time.Instant;

/**
 * Server side session metadata used by SIMPLE and MIXIN modes.
 *
 * @author MoLi
 */
public class JwtSession {

    private String sessionId;

    private String tokenId;

    private JwtUser user;

    private Instant createdAt;

    private Instant lastAccessAt;

    private Instant expiresAt;

    private Instant activeExpiresAt;

    private boolean expired;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTokenId() {
        return tokenId;
    }

    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }

    public JwtUser getUser() {
        return user;
    }

    public void setUser(JwtUser user) {
        this.user = user;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastAccessAt() {
        return lastAccessAt;
    }

    public void setLastAccessAt(Instant lastAccessAt) {
        this.lastAccessAt = lastAccessAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getActiveExpiresAt() {
        return activeExpiresAt;
    }

    public void setActiveExpiresAt(Instant activeExpiresAt) {
        this.activeExpiresAt = activeExpiresAt;
    }

    public boolean isExpired() {
        return expired;
    }

    public void setExpired(boolean expired) {
        this.expired = expired;
    }
}
