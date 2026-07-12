package com.bingchunmoli.security.web;

/**
 * Force logout request by token.
 *
 * @author MoLi
 */
public class ForceLogoutTokenRequest {

    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
