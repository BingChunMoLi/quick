package com.bingchunmoli.security.jwt;

import java.time.Instant;

/**
 * Issued JWT token metadata.
 *
 * @author MoLi
 */
public class JwtToken {

    private String token;

    private String tokenType = "Bearer";

    private String tokenId;

    private String sessionId;

    private JwtMode mode;

    private Instant issuedAt;

    private Instant expiresAt;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getTokenId() {
        return tokenId;
    }

    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public JwtMode getMode() {
        return mode;
    }

    public void setMode(JwtMode mode) {
        this.mode = mode;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
