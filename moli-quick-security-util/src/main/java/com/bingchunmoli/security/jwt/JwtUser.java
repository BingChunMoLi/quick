package com.bingchunmoli.security.jwt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Login user payload for JWT.
 *
 * @author MoLi
 */
public class JwtUser {

    private String userId;

    private String username;

    private List<String> authorities = new ArrayList<>();

    private Map<String, Object> attributes = new HashMap<>();

    public JwtUser() {
    }

    public JwtUser(String userId, String username, List<String> authorities, Map<String, Object> attributes) {
        this.userId = userId;
        this.username = username;
        this.authorities = authorities == null ? new ArrayList<>() : authorities;
        this.attributes = attributes == null ? new HashMap<>() : attributes;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<String> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(List<String> authorities) {
        this.authorities = authorities == null ? new ArrayList<>() : authorities;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes == null ? new HashMap<>() : attributes;
    }
}
