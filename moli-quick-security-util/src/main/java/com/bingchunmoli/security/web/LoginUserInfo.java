package com.bingchunmoli.security.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.time.Instant;
import java.util.List;

/**
 * Current login user view.
 *
 * @author MoLi
 */
public class LoginUserInfo {

    private String username;

    private Object principal;

    private List<String> authorities;

    private String sessionId;

    private String token;

    private String tokenType;

    private Instant expiresAt;

    private String mode;

    public LoginUserInfo() {
    }

    public LoginUserInfo(String username, Object principal, List<String> authorities, String sessionId) {
        this.username = username;
        this.principal = principal;
        this.authorities = authorities;
        this.sessionId = sessionId;
    }

    public static LoginUserInfo of(Authentication authentication, String sessionId) {
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return new LoginUserInfo(authentication.getName(), authentication.getPrincipal(), authorities, sessionId);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Object getPrincipal() {
        return principal;
    }

    public void setPrincipal(Object principal) {
        this.principal = principal;
    }

    public List<String> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(List<String> authorities) {
        this.authorities = authorities;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

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

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
